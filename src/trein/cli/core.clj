(ns trein.cli.core
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [trein.adapters.db-rest :as db-rest]
            [trein.adapters.static :as static-adapter]
            [trein.cli.formatter :as fmt]
            [trein.routes :as routes])
  (:gen-class))

(def ^:private cli-options
  [["-s" "--static" "Use built-in static data instead of live API (offline mode)"]
   ["-h" "--help"   "Show this help message"]])

(defn- usage [summary]
  (str/join \newline
            ["Usage: trein [options] <city>"
             ""
             "Returns international train connections departing from <city>."
             ""
             "Any European city with long-distance train service."
             ""
             "Options:"
             summary]))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (println (usage summary))

      (seq errors)
      (do (println "Error:" (first errors))
          (println (usage summary))
          (System/exit 1))

      (empty? arguments)
      (do (println (usage summary))
          (System/exit 1))

      :else
      (let [city (first arguments)
            repo (if (:static options)
                   (static-adapter/make)
                   (db-rest/make))]
        (try
          (let [connections (routes/find-connections repo city)]
            (fmt/print-connections connections))
          (catch clojure.lang.ExceptionInfo e
            (let [data (ex-data e)]
              (case (:type data)
                :invalid-input
                (do (println (str "Error: " (ex-message e)))
                    (System/exit 1))
                :http-error
                (do (println (str "Error: API request failed (HTTP " (:status data) ")"
                                  (when (:reason data) (str " — " (:reason data)))
                                  "."))
                    (println "Tip: use --static for offline mode.")
                    (System/exit 1))
                :api-error
                (do (println (str "Error: " (:message data) "."))
                    (println "Tip: use --static for offline mode.")
                    (System/exit 1))
                :station-not-found
                (do (println (str "Error: " (ex-message e)))
                    (System/exit 1))
                ;; Re-throw unknown errors
                (throw e))))
          (catch java.net.ConnectException _
            (println "Error: Could not connect to the API. Check your internet connection.")
            (println "Tip: use --static for offline mode.")
            (System/exit 1)))))))
