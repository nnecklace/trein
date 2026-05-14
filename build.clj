(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib     'trein/trein)
(def version "0.1.0")
(def main-cls 'trein.cli.core)
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s.jar" (name lib) version))
(def basis (b/create-basis {:project "deps.edn"}))

(defn uber [_]
  (b/delete {:path "target"})
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     basis
           :main      main-cls})
  (println (str "Built: " uber-file)))
