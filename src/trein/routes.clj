(ns trein.routes
  "Route discovery use-case.

   Semantics: 'Which cities in other countries can I reach by direct
   long-distance train from <city>, based on today's schedule?'

   This is route discovery, not schedule lookup. The output is one entry
   per reachable international destination city, regardless of how many
   individual departures serve that route. Departure times are not
   included — only the fact that the route exists today.

   Rules:
     - Direct trains only (no transfers)
     - Destination country must differ from origin country
     - One entry per destination city, sorted alphabetically
     - Train's terminus is used as destination (intermediate stops invisible)"
  (:require [clojure.string :as str]
            [trein.ports :as ports]))

(defn find-connections
  "Returns a sorted vec of international Connection maps reachable from
   raw-city by direct train. Connections to cities in the same country as
   the departure city are excluded.

   The source country is determined by the adapter from the station it
   resolves, not from a hardcoded map.

   Throws ex-info with :type :invalid-input if city is blank."
  [repo raw-city]
  (let [city (str/trim (str raw-city))]
    (when (str/blank? city)
      (throw (ex-info "City name must not be blank."
                      {:type :invalid-input})))
    (let [connections    (ports/find-connections repo city)
          source-country (get-in (first connections) [:from :country])]
      (->> connections
           (remove #(= (get-in % [:to :country]) source-country))
           (sort-by #(get-in % [:to :name]))
           vec))))
