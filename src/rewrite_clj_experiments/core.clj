(ns rewrite-clj-experiments.core
  (:require
    [rewrite-clj-experiments.io :as io]
    [borkdude.rewrite-edn :as rewrite-edn]
    [clojure.pprint :refer [pprint]]
    [clojure.walk :refer [prewalk]]
    [rewrite-clj.node :as n]
    [rewrite-clj.parser :as parser]
    [rewrite-clj.zip :as z]
    [cljstyle.config :as fmt-config]
    [cljfmt.core :as cljfmt]
    [rewrite-clj-experiments.rewrite-clj-override]))


(defmulti inject :type)

(defn topmost [z-loc]
  (loop [z-loc z-loc]
    (if-let [parent (z/up z-loc)]
      (recur parent)
      z-loc)))

(defn format-str [s]
  (cljfmt/reformat-string
    s
    {:indentation?                          true
     :split-keypairs-over-multiple-lines?   true
     :insert-missing-whitespace?            true
     :remove-multiple-non-indenting-spaces? true}))

(defn reformat-string [form-string rules-config]
  (-> form-string
      (format-str)
      (parser/parse-string-all)
      ;(format/reformat-form rules-config)
      ))

(defn format-zloc [zloc]
  (z/replace zloc (reformat-string (z/string zloc) (:rules fmt-config/default-config))))

(defn conflicting-keys
  [node value-keys]
  (filter #(contains? node %)
          value-keys))

(defn zipper-insert-kw-pairs
  [zloc kw-zipper]
  (let [k kw-zipper
        v (z/right kw-zipper)]
    (if-not (and (some? k) (some? v))
      (format-zloc (z/up zloc))
      (recur
        (-> zloc
            (z/insert-right (z/node k))
            (z/right)
            (z/insert-newline-left)
            (z/insert-right (z/node v))
            (z/right))
        (-> kw-zipper
            (z/right)
            (z/right))))))

(defn edn-merge-value [value]
  (fn [node]
    (if-let [inside-map (z/down node)]
      (-> inside-map
          (z/rightmost)
          (zipper-insert-kw-pairs (z/down value)))
      (z/replace node (z/node (format-zloc value))))))

(defn edn-safe-merge [zloc value]
  (try
    (let [value-keys   (keys (z/sexpr value))
          target-value (z/sexpr zloc)]
      (let [conflicts (conflicting-keys target-value value-keys)]
        (if (seq conflicts)
          (do (println "file has conflicting keys! Skipping"
                       "\n keys:" conflicts)
              zloc)
          ((edn-merge-value value) zloc))))
    (catch Exception e
      (throw (Exception. (str "error merging!\n target:" zloc "\n value:" value) e)))))

(defn zloc-get-in
  [zloc [k & ks]]
  (if-not k
    zloc
    (recur (z/get zloc k) ks)))

(defn zloc-conj [zloc value]
  (-> zloc
      (z/down)
      (z/rightmost)
      (z/insert-right (z/node value))
      (z/up)))

(defn z-assoc-in [zloc [k & ks] v]
  (if (empty? ks)
    (z/assoc zloc k v)
    (z/assoc zloc k (z/node (z-assoc-in (z/get zloc k) ks v)))))

(defn z-update-in [zloc [k & ks] f]
  (if k
    (z-update-in (z/get zloc k) ks f)
    (when zloc
      (f zloc))))

(defn normalize-value [value]
  (if (string? value)
    (z/of-string (str "\"" value "\""))
    (z/replace (z/of-string "")
               (n/sexpr value))))

(defmethod inject :edn [{:keys [data target action value ctx]}]
  (binding [*print-namespace-maps* false]
    (let [value (normalize-value value)]
      (topmost
        (case action
          :append
          (if (empty? target)
            (zloc-conj data value)
            (or (z-update-in data target #(zloc-conj % value))
                (println "could not find injection target:" target "in data:" (z/node data))))
          :merge
          (if-let [zloc (zloc-get-in data target)]
            (edn-safe-merge zloc value)
            (println "could not find injection target:" target "in data:" (z/node data))))))))

(comment

  (->
    (inject
      {:type   :edn
       :data   (z/of-string "{:z :r :deps {:foo :bar}}")
       :target []
       :action :merge
       :value  (io/str->edn "{:db.sql/connection #profile\n {:prod {:jdbc-url #env JDBC_URL}}}")})
    z/node
    str)

  (let [data  (z/of-string (slurp "resources/system.edn"))
        value (io/str->edn (slurp "resources/injection.edn"))]

    ))
