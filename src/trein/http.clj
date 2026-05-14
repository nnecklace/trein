(ns trein.http
  (:require [babashka.http-client :as http] ;; org.babashka/http-client
            [jsonista.core :as json]))

(defn get-json
  "Makes a GET request to url with the given query-params map (string keys).
   Returns the parsed JSON body as a Clojure map with keyword keys.
   Throws ex-info with :type :http-error on non-200 status or network error."
  [url params]
  (let [response (http/get url
                           {:query-params params
                            :timeout      15000
                            :throw        false   ; handle status ourselves
                            :headers      {"Accept" "application/json"
                                           "User-Agent" "trein-cli/0.1.0"}})]
    (when-not (= 200 (:status response))
      (let [parsed-reason (try
                            (:message (json/read-value (:body response) json/keyword-keys-object-mapper))
                            (catch Exception _ nil))
            status-reason (case (:status response)
                            429 "Too Many Requests"
                            500 "Internal Server Error"
                            502 "Bad Gateway"
                            503 "Service Unavailable (likely rate-limited)"
                            504 "Gateway Timeout"
                            nil)
            reason        (or parsed-reason status-reason)]
        (throw (ex-info (str "HTTP " (:status response) " from " url)
                        {:type   :http-error
                         :url    url
                         :status (:status response)
                         :reason reason
                         :body   (:body response)}))))
    (json/read-value (:body response) json/keyword-keys-object-mapper)))
