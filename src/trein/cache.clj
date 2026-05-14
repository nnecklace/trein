(ns trein.cache
  (:require [clojure.java.io :as io]
            [jsonista.core :as json])
  (:import [java.time Instant]))

(def ^:private cache-dir
  (str (System/getProperty "user.home") "/.trein/cache"))

(defn- cache-file ^java.io.File [key]
  (io/file cache-dir (str (Math/abs (hash key)) ".json")))

(defn- now-epoch []
  (.getEpochSecond (Instant/now)))

(defn get-cached
  "Returns the cached value for key if it exists and is not older than
   ttl-seconds. Returns nil on cache miss, expired entry, or read error."
  [key ttl-seconds]
  (let [f (cache-file key)]
    (when (.exists f)
      (try
        (let [data      (json/read-value (slurp f) json/keyword-keys-object-mapper)
              cached-at (:cached-at data)
              age       (- (now-epoch) (long cached-at))]
          (when (< age ttl-seconds)
            (:value data)))
        (catch Exception _
          nil)))))

(defn put-cached!
  "Stores value under key. Creates cache directory if needed."
  [key value]
  (let [f (cache-file key)]
    (io/make-parents f)
    (spit f (json/write-value-as-string {:cached-at (now-epoch)
                                         :value     value}))))
