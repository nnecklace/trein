(ns trein.adapters.db-rest-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [trein.adapters.db-rest :as db-rest]))

;; ---------------------------------------------------------------------------
;; Fixture loading
;; ---------------------------------------------------------------------------

(defn- load-fixture [filename]
  (-> (io/resource (str "fixtures/" filename))
      slurp
      (json/read-value json/keyword-keys-object-mapper)))

(defn- call-private [ns-sym fn-sym & args]
  (apply (ns-resolve ns-sym fn-sym) args))

;; ---------------------------------------------------------------------------
;; UIC country resolution
;; ---------------------------------------------------------------------------

(deftest station-id->country-resolves-known-prefixes
  (testing "derives country from IBNR station ID prefix"
    (let [resolve #(call-private 'trein.adapters.db-rest 'station-id->country %)]
      (is (= "NL" (resolve "8400058")))
      (is (= "DE" (resolve "8011160")))
      (is (= "FR" (resolve "8700014")))
      (is (= "BE" (resolve "8800004")))
      (is (= "AT" (resolve "8100173")))
      (is (= "CH" (resolve "8503000")))
      (is (= "PL" (resolve "5100005")))
      (is (= "CZ" (resolve "5400001")))
      (is (= "HU" (resolve "5500728")))
      (is (= "SE" (resolve "7400004")))
      (is (= "GB" (resolve "7004428")))
      (is (= "DK" (resolve "8600001")))
      (is (= "LU" (resolve "8200100")))
      (is (= "IT" (resolve "8300001"))))))

(deftest station-id->country-returns-nil-for-unknown
  (testing "returns nil for non-IBNR or unrecognised prefixes"
    (let [resolve #(call-private 'trein.adapters.db-rest 'station-id->country %)]
      (is (nil? (resolve nil)))
      (is (nil? (resolve "")))
      (is (nil? (resolve "99999")))
      (is (nil? (resolve "abc"))))))

;; ---------------------------------------------------------------------------
;; TripId parsing
;; ---------------------------------------------------------------------------

(deftest parse-trip-destination-id-extracts-to-station
  (testing "extracts TO# station ID from tripId"
    (let [parse #(call-private 'trein.adapters.db-rest 'parse-trip-destination-id %)]
      (is (= "8700014" (parse "2|...#TO#8700014#TT#1443#")))
      (is (= "8800004" (parse "2|...#FR#8400058#FT#1110#TO#8800004#TT#1403#")))
      (is (nil? (parse nil)))
      (is (nil? (parse "no-to-field"))))))

;; ---------------------------------------------------------------------------
;; Integration-style test using fixture data
;; ---------------------------------------------------------------------------

(deftest parses-berlin-fixture-correctly
  (testing "departure->connection parses fixture departures with resolvable destinations"
    (let [parse      #(call-private 'trein.adapters.db-rest 'departure->connection
                                    "Berlin" "DE" %)
          fixture    (load-fixture "db-rest-berlin.json")
          departures (:departures fixture)
          parsed     (->> departures
                          (map parse)
                          (remove nil?))]
      ;; Should parse departures with valid tripId TO# and known UIC prefix
      (is (>= (count parsed) 10))
      (is (every? :from parsed))
      (is (every? :to parsed))
      (is (every? #(= "DE" (get-in % [:from :country])) parsed))
      (let [to-countries (set (map #(get-in % [:to :country]) parsed))]
        ;; International destinations from the real Berlin fixture
        (is (contains? to-countries "CH"))   ; ICE 373 → Chur
        (is (contains? to-countries "DK"))   ; RJ 384 → Copenhagen
        (is (contains? to-countries "HU"))   ; RJ 175 → Budapest
        ;; Domestic destinations should also be resolved
        (is (contains? to-countries "DE"))))))  ; Munich, Cologne, etc.

(deftest merge-connections-deduplicates-by-destination
  (testing "groups connections with the same destination"
    (let [merge  #(call-private 'trein.adapters.db-rest 'merge-connections %)
          conns  [{:from {:name "Berlin" :country "DE"}
                   :to   {:name "Amsterdam" :country "NL"}
                   :operator "DB" :train-numbers ["ICE 145"]}
                  {:from {:name "Berlin" :country "DE"}
                   :to   {:name "Amsterdam" :country "NL"}
                   :operator "DB" :train-numbers ["ICE 147"]}
                  {:from {:name "Berlin" :country "DE"}
                   :to   {:name "Paris" :country "FR"}
                   :operator "DB" :train-numbers ["ICE 371"]}]
          merged (merge conns)]
      (is (= 2 (count merged)))
      (let [amsterdam (first (filter #(= "Amsterdam" (get-in % [:to :name])) merged))]
        (is (= #{"ICE 145" "ICE 147"} (set (:train-numbers amsterdam))))))))

;; ---------------------------------------------------------------------------
;; Live integration test (skipped by default)
;; ---------------------------------------------------------------------------

(deftest ^:integration live-api-returns-connections-for-berlin
  (testing "real API call returns international connections from Berlin"
    (let [adapter (db-rest/make)
          conns   (trein.ports/find-connections adapter "Berlin")]
      (is (>= (count conns) 3))
      (is (every? :from conns))
      (is (every? :to conns))
      (let [countries (set (map #(get-in % [:to :country]) conns))]
        (is (some #{"NL" "FR" "BE" "AT" "CH" "PL"} countries))))))
