(ns trein.adapters.static
  (:require [trein.ports :as ports]))

;; Hardcoded known international train connections.
;; Used as the fallback adapter (--static flag) and as fixture data in unit tests.
;; Train numbers represent typical services; specific times vary by schedule.
(def ^:private connections
  {"Amsterdam"
   [{:from {:name "Amsterdam" :country "NL"}
     :to   {:name "Berlin"    :country "DE"}
     :operator      "DB"
     :train-numbers ["ICE 145"]}
    {:from {:name "Amsterdam" :country "NL"}
     :to   {:name "Brussels"  :country "BE"}
     :operator      "Eurostar"
     :train-numbers ["9355"]}
    {:from {:name "Amsterdam" :country "NL"}
     :to   {:name "London"    :country "GB"}
     :operator      "Eurostar"
     :train-numbers ["9117"]}
    {:from {:name "Amsterdam" :country "NL"}
     :to   {:name "Paris"     :country "FR"}
     :operator      "Eurostar"
     :train-numbers ["9355"]}
    {:from {:name "Amsterdam" :country "NL"}
     :to   {:name "Cologne"   :country "DE"}
     :operator      "DB"
     :train-numbers ["ICE 227"]}
    {:from {:name "Amsterdam" :country "NL"}
     :to   {:name "Frankfurt" :country "DE"}
     :operator      "DB"
     :train-numbers ["ICE 227"]}]

   "Berlin"
   [{:from {:name "Berlin" :country "DE"}
     :to   {:name "Amsterdam" :country "NL"}
     :operator      "DB"
     :train-numbers ["ICE 146"]}
    {:from {:name "Berlin" :country "DE"}
     :to   {:name "Brussels"  :country "BE"}
     :operator      "DB"
     :train-numbers ["ICE 16"]}
    {:from {:name "Berlin" :country "DE"}
     :to   {:name "Paris"     :country "FR"}
     :operator      "DB"
     :train-numbers ["ICE 371"]}
    {:from {:name "Berlin" :country "DE"}
     :to   {:name "Vienna"    :country "AT"}
     :operator      "DB"
     :train-numbers ["RJX 60"]}
    {:from {:name "Berlin" :country "DE"}
     :to   {:name "Warsaw"    :country "PL"}
     :operator      "PKP IC"
     :train-numbers ["EC 45"]}
    {:from {:name "Berlin" :country "DE"}
     :to   {:name "Zurich"    :country "CH"}
     :operator      "DB"
     :train-numbers ["ICE 75"]}
    {:from {:name "Berlin" :country "DE"}
     :to   {:name "Prague"    :country "CZ"}
     :operator      "DB"
     :train-numbers ["EC 379"]}]

   "Paris"
   [{:from {:name "Paris" :country "FR"}
     :to   {:name "Amsterdam" :country "NL"}
     :operator      "Eurostar"
     :train-numbers ["9356"]}
    {:from {:name "Paris" :country "FR"}
     :to   {:name "Berlin"    :country "DE"}
     :operator      "DB"
     :train-numbers ["ICE 372"]}
    {:from {:name "Paris" :country "FR"}
     :to   {:name "Brussels"  :country "BE"}
     :operator      "Eurostar"
     :train-numbers ["9320"]}
    {:from {:name "Paris" :country "FR"}
     :to   {:name "London"    :country "GB"}
     :operator      "Eurostar"
     :train-numbers ["9026"]}
    {:from {:name "Paris" :country "FR"}
     :to   {:name "Zurich"    :country "CH"}
     :operator      "SBB/DB"
     :train-numbers ["TGV 9211"]}
    {:from {:name "Paris" :country "FR"}
     :to   {:name "Frankfurt" :country "DE"}
     :operator      "DB"
     :train-numbers ["TGV 9550"]}
    {:from {:name "Paris" :country "FR"}
     :to   {:name "Geneva"    :country "CH"}
     :operator      "SBB"
     :train-numbers ["TGV 9771"]}]

   "Brussels"
   [{:from {:name "Brussels" :country "BE"}
     :to   {:name "Amsterdam" :country "NL"}
     :operator      "Eurostar"
     :train-numbers ["9356"]}
    {:from {:name "Brussels" :country "BE"}
     :to   {:name "Berlin"    :country "DE"}
     :operator      "DB"
     :train-numbers ["ICE 17"]}
    {:from {:name "Brussels" :country "BE"}
     :to   {:name "Paris"     :country "FR"}
     :operator      "Eurostar"
     :train-numbers ["9321"]}
    {:from {:name "Brussels" :country "BE"}
     :to   {:name "London"    :country "GB"}
     :operator      "Eurostar"
     :train-numbers ["9114"]}
    {:from {:name "Brussels" :country "BE"}
     :to   {:name "Cologne"   :country "DE"}
     :operator      "DB"
     :train-numbers ["ICE 15"]}
    {:from {:name "Brussels" :country "BE"}
     :to   {:name "Frankfurt" :country "DE"}
     :operator      "DB"
     :train-numbers ["ICE 15"]}
    {:from {:name "Brussels" :country "BE"}
     :to   {:name "Rotterdam" :country "NL"}
     :operator      "Eurostar"
     :train-numbers ["9356"]}]})

(defrecord StaticAdapter []
  ports/RouteRepository
  (find-connections [_this from-city]
    (get connections from-city [])))

(defn make [] (->StaticAdapter))
