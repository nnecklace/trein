(ns trein.ports)

(defprotocol RouteRepository
  (find-connections [this from-city]
    "Returns a seq of Connection maps departing from the given city name string.
     The city name must be a canonical supported city (e.g. \"Berlin\").
     Connections to cities in the same country as from-city may be included;
     filtering to cross-border only is done in the use-case layer."))
