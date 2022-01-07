(defproject rewrite-clj-experiments "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [borkdude/rewrite-edn "0.0.2"]
                 [rewrite-clj "1.0.644-alpha"]
                 [mvxcvi/cljstyle "0.15.0"]
                 [cljfmt "0.8.0"]]
  :repl-options {:init-ns rewrite-clj-experiments.core})
