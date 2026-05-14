(ns trein.cli.formatter)

(defn format-connection
  "Returns a string representation of a single connection.
   Example: \"Amsterdam → Berlin (ICE 145)\""
  [{:keys [from to operator train-numbers]}]
  (let [from-name (:name from)
        to-name   (:name to)
        label     (or (first train-numbers) operator)]
    (if label
      (str from-name " → " to-name " (" label ")")
      (str from-name " → " to-name))))

(defn print-connections
  "Prints a list of connections to stdout. Prints a message if the list is empty."
  [connections]
  (if (empty? connections)
    (println "No international connections found.")
    (doseq [conn connections]
      (println (format-connection conn)))))
