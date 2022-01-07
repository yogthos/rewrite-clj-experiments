(ns rewrite-clj-experiments.io
  (:require
    [clojure.edn :as edn]
    [clojure.tools.reader :as reader]))

(defrecord Tag [label value])

(def edn-reader-opts {:default (fn [tag value]
                                 (Tag. (str tag) value))
                      :readers reader/default-data-readers})

(defmethod print-method Tag
  [{:keys [label value]} writer]
  (.write writer (str "#" (name label) " " value)))

(defmethod print-dup Tag
  [{:keys [label value]} writer]
  (.write writer (str "#" (name label) " " value)))

(defn str->edn [config]
  (edn/read-string edn-reader-opts config))

(defn edn->str [edn]
  (with-out-str (prn edn)))

(defn update-edn-file [path f]
  (spit
    path
    (-> (slurp path)
        (str->edn)
        (f)
        (edn->str))))

(comment
  (edn->str (str->edn (slurp "resources/injection.edn")))
  )
