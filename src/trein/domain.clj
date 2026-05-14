(ns trein.domain)

;; Malli schemas — used for documentation and optional validation

(def City
  [:map
   [:name    string?]
   [:country string?]])

(def Connection
  [:map
   [:from          City]
   [:to            City]
   [:operator      {:optional true} string?]
   [:train-numbers {:optional true} [:vector string?]]])
