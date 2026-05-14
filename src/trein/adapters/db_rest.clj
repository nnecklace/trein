(ns trein.adapters.db-rest
  (:require [clojure.string :as str]
            [trein.cache :as cache]
            [trein.http :as http]
            [trein.ports :as ports]))

(def ^:private api-base
  (or (System/getenv "DB_REST_URL") "http://localhost:3000"))
(def ^:private cache-ttl (* 24 60 60)) ; 24 hours — route discovery, not real-time schedule

;; ---------------------------------------------------------------------------
;; Country resolution via UIC station ID prefix
;; The first 2 digits of an IBNR station ID encode the UIC country code.
;; This eliminates the need for manually maintained country lookup maps.
;; ---------------------------------------------------------------------------

(def ^:private uic-country
  "UIC country codes → ISO 3166-1 alpha-2. Derived from the IBNR station
   ID prefix (first 2 digits of 7-digit IDs)."
  {10 "FI" 20 "RU" 21 "BY" 24 "LT" 25 "LV" 26 "EE"
   51 "PL" 54 "CZ" 55 "HU" 56 "SK"
   70 "GB" 71 "ES" 72 "RS" 73 "GR" 74 "SE" 75 "TR" 76 "NO"
   78 "HR" 79 "SI"
   80 "DE" 81 "AT" 82 "LU" 83 "IT" 84 "NL" 85 "CH" 86 "DK"
   87 "FR" 88 "BE" 94 "RO" 95 "BG"})

(defn- station-id->country
  "Returns the ISO country code for a 7-digit IBNR station ID, or nil.
   Only 7-digit IDs are valid IBNR codes; shorter IDs (e.g. 6-digit local
   transit stops like 733140) are ignored to avoid false UIC prefix matches."
  [station-id]
  (when (and station-id (= 7 (count station-id)))
    (try
      (get uic-country (Integer/parseInt (subs station-id 0 2)))
      (catch NumberFormatException _ nil))))

;; Station name cache — populated lazily via /stops/:id API calls.
;; Avoids repeated lookups for the same station across departures.
(defonce ^:private station-name-cache (atom {}))

(defn- lookup-station-name
  "Returns the station name for an IBNR station ID. Checks the in-memory
   cache first, then calls /stops/:id. Returns nil on failure."
  [station-id]
  (or (get @station-name-cache station-id)
      (try
        (let [result (http/get-json (str api-base "/stops/" station-id) {})
              name   (:name result)]
          (when name
            (swap! station-name-cache assoc station-id name))
          name)
        (catch Exception _ nil))))

;; ---------------------------------------------------------------------------
;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- parse-trip-destination-id
  "Extracts the destination station ID from a DB REST tripId string.
   The tripId encodes TO#STATION_ID#TT#TIME. Returns nil if unparseable."
  [trip-id]
  (when (and trip-id (str/includes? trip-id "TO#"))
    (let [after-to (second (str/split trip-id #"TO#"))]
      (when after-to
        (first (str/split after-to #"#"))))))

(defn- search-station-id
  "Searches the DB REST /locations endpoint and returns the ID of the best
   long-distance station matching the city query. Works for any city name."
  [city-name]
  (let [results (http/get-json (str api-base "/locations")
                               {"query"     city-name
                                "results"   "5"
                                "stops"     "true"
                                "poi"       "false"
                                "addresses" "false"})]
    (->> results
         (filter #(and (= "station" (get % :type))
                       (get-in % [:products :nationalExpress])))
         first
         :id)))

(def ^:private departure-params
  "No product filter — fetch all departures so we get full 1-hour coverage
   per request with no gaps. Non-train departures (bus, tram, subway) are
   discarded by resolve-destination (no country match → nil → dropped)."
  {"duration" "240"
   "results"  "1000"})

(defn- fetch-departures-at
  "Fetches departures from a stop starting at the given ISO-8601 time string."
  [station-id when-str]
  (let [params (cond-> departure-params
                 when-str (assoc "when" when-str))
        result (http/get-json (str api-base "/stops/" station-id "/departures")
                              params)]
    (when (:message result)
      (throw (ex-info (str "API error: " (:message result))
                      {:type :api-error :message (:message result)})))
    (or (:departures result) [])))

(defn- fetch-departures
  "Fetches departures across a full day by querying in 1-hour time slices.
   The upstream API caps each response to ~1 hour of departures regardless
   of the requested duration, so we step through 24 offsets."
  [station-id]
  (let [now    (java.time.ZonedDateTime/now (java.time.ZoneId/of "Europe/Berlin"))
        fmt    java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME
        slices (map #(.format (.plusHours now (long %)) fmt) (range 24))]
    (->> slices
         (mapcat #(fetch-departures-at station-id %))
         vec)))

(defn- resolve-destination
  "Determines the destination {:name city :country cc} for a departure.
   Uses the destination station ID from the tripId to derive country via
   UIC prefix. The station name comes from the direction field when
   available, otherwise from a /stops/:id API lookup (cached in memory)."
  [departure]
  (when-let [dest-id (parse-trip-destination-id (:tripId departure))]
    (when-let [country (station-id->country dest-id)]
      (let [name (or (get-in departure [:destination :name])
                     (:direction departure)
                     (lookup-station-name dest-id))]
        (when name
          {:name name :country country})))))

(defn- departure->connection
  "Converts a DB REST departure map to a Connection map.
   Returns nil if the product is not long-distance, or if the destination
   country cannot be determined."
  [from-city from-country departure]
  (let [dest      (resolve-destination departure)
        line-name (get-in departure [:line :name])
        operator  (or (get-in departure [:line :operator :name]) "DB")]
    (when dest
      {:from          {:name from-city :country from-country}
       :to            dest
       :operator      operator
       :train-numbers (when line-name [line-name])})))

(defn- merge-connections
  "Groups connections by destination city, merging train numbers."
  [connections]
  (->> connections
       (group-by #(get-in % [:to :name]))
       vals
       (map (fn [group]
              (let [base    (first group)
                    numbers (->> (mapcat :train-numbers group)
                                 distinct
                                 (take 5)
                                 vec)]
                (assoc base :train-numbers numbers))))))

;; ---------------------------------------------------------------------------
;; Adapter
;; ---------------------------------------------------------------------------

(defn- resolve-station-id
  "Returns the station ID for a city. Checks the in-memory cache first,
   then falls back to an API search. Works for any city name."
  [city-name station-id-cache]
  (or (get @station-id-cache city-name)
      (let [id (search-station-id city-name)]
        (when id (swap! station-id-cache assoc city-name id))
        id)))

(defrecord DbRestAdapter [station-id-cache]
  ports/RouteRepository
  (find-connections [_this from-city]
    (let [cache-key (str "db-rest-" from-city)
          cached    (cache/get-cached cache-key cache-ttl)]
      (or cached
          (let [station-id (resolve-station-id from-city station-id-cache)]
            (if-not station-id
              (throw (ex-info (str "Could not find station for: " from-city)
                              {:type :station-not-found :city from-city}))
              (let [from-country (station-id->country station-id)
                    departures   (fetch-departures station-id)
                    connections  (->> departures
                                      (map #(departure->connection from-city from-country %))
                                      (remove nil?)
                                      merge-connections)]
                ;; Only cache non-empty results so a transient API failure
                ;; doesn't persist as a stale empty cache entry.
                (when (seq connections)
                  (cache/put-cached! cache-key connections))
                connections)))))))

(defn make
  "Creates a new DbRestAdapter with an empty in-memory station ID cache."
  []
  (->DbRestAdapter (atom {})))
