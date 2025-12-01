(ns user
  (:require
   [clojure.tools.namespace.repl :refer [refresh]]
   [spellchecker-backend.core :as core]))

(defn go []
  (core/start!))

(defn halt []
  (core/stop!))

(defn reset []
  (halt)
  (refresh :after 'user/go))