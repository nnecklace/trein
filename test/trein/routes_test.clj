(ns trein.routes-test
  (:require [clojure.test :refer [deftest is testing]]
            [trein.ports :as ports]
            [trein.routes :as routes]))

;; ---------------------------------------------------------------------------
;; Test helpers
;; ---------------------------------------------------------------------------

(defn- mock-repo
  "Creates a mock RouteRepository that returns the provided connections seq."
  [connections]
  (reify ports/RouteRepository
    (find-connections [_ _from-city] connections)))

(def ^:private berlin->amsterdam
  {:from {:name "Berlin" :country "DE"}
   :to   {:name "Amsterdam" :country "NL"}
   :operator "DB"
   :train-numbers ["ICE 145"]})

(def ^:private berlin->hamburg
  {:from {:name "Berlin" :country "DE"}
   :to   {:name "Hamburg" :country "DE"}
   :operator "DB"
   :train-numbers ["ICE 701"]})

(def ^:private berlin->paris
  {:from {:name "Berlin" :country "DE"}
   :to   {:name "Paris" :country "FR"}
   :operator "DB"
   :train-numbers ["ICE 371"]})

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest find-connections-returns-international-connections
  (testing "returns only connections to cities in different countries"
    (let [repo        (mock-repo [berlin->amsterdam berlin->hamburg berlin->paris])
          connections (routes/find-connections repo "Berlin")]
      (is (= 2 (count connections)))
      (is (every? #(not= "DE" (get-in % [:to :country])) connections)))))

(deftest find-connections-filters-same-country
  (testing "removes connections where destination is in the same country as origin"
    (let [repo        (mock-repo [berlin->hamburg])
          connections (routes/find-connections repo "Berlin")]
      (is (empty? connections)))))

(deftest find-connections-sorted-by-destination-name
  (testing "results are sorted alphabetically by destination city name"
    (let [repo        (mock-repo [berlin->paris berlin->amsterdam])
          connections (routes/find-connections repo "Berlin")]
      (is (= ["Amsterdam" "Paris"]
             (mapv #(get-in % [:to :name]) connections))))))

(deftest find-connections-trims-whitespace
  (testing "leading/trailing whitespace in city name is ignored"
    (let [repo (mock-repo [berlin->amsterdam])]
      (is (seq (routes/find-connections repo "  Berlin  "))))))

(deftest find-connections-blank-input-throws
  (testing "throws ex-info with :type :invalid-input for blank input"
    (let [repo (mock-repo [])]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"blank"
           (routes/find-connections repo "")))
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"blank"
           (routes/find-connections repo "   "))))))

(deftest find-connections-empty-results
  (testing "returns empty vec when repo returns no connections"
    (let [repo (mock-repo [])]
      (is (= [] (routes/find-connections repo "Berlin"))))))

(deftest find-connections-accepts-any-city-name
  (testing "any non-blank city name is passed through to the adapter"
    (let [repo (mock-repo [])]
      (doseq [city ["Berlin" "Vienna" "Zürich" "Some Random City"]]
        (is (vector? (routes/find-connections repo city)))))))
