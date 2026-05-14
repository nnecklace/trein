(ns trein.formatter-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [trein.cli.formatter :as fmt]))

(def ^:private conn-with-train
  {:from {:name "Berlin" :country "DE"}
   :to   {:name "Amsterdam" :country "NL"}
   :operator      "DB"
   :train-numbers ["ICE 145"]})

(def ^:private conn-with-operator-only
  {:from {:name "Berlin" :country "DE"}
   :to   {:name "Vienna" :country "AT"}
   :operator      "ÖBB"
   :train-numbers []})

(def ^:private conn-no-label
  {:from {:name "Berlin" :country "DE"}
   :to   {:name "Paris"  :country "FR"}})

(deftest format-connection-with-train-number
  (testing "shows first train number in parentheses"
    (is (= "Berlin → Amsterdam (ICE 145)"
           (fmt/format-connection conn-with-train)))))

(deftest format-connection-uses-operator-when-no-train-number
  (testing "falls back to operator name when train-numbers is empty"
    (is (= "Berlin → Vienna (ÖBB)"
           (fmt/format-connection conn-with-operator-only)))))

(deftest format-connection-no-label
  (testing "shows just from → to when no operator or train number"
    (is (= "Berlin → Paris"
           (fmt/format-connection conn-no-label)))))

(deftest print-connections-outputs-each-connection
  (testing "prints one line per connection"
    (let [output (with-out-str
                   (fmt/print-connections [conn-with-train conn-with-operator-only]))]
      (is (str/includes? output "Berlin → Amsterdam (ICE 145)"))
      (is (str/includes? output "Berlin → Vienna (ÖBB)"))
      (is (= 2 (count (str/split-lines (str/trim output))))))))

(deftest print-connections-empty-list
  (testing "prints a 'no connections' message for an empty list"
    (let [output (with-out-str (fmt/print-connections []))]
      (is (str/includes? output "No international connections found")))))
