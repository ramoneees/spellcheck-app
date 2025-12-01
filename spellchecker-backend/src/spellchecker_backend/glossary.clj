(ns spellchecker-backend.glossary
  (:require
   [clojure.java.io :as io]
   [clojure.data.csv :as csv]
   [clojure.string :as str]))

(defn- load-glossary-csv [resource-path]
  (let [raw (slurp (io/resource resource-path))
        rows (csv/read-csv raw)
        header (mapv (comp keyword str/trim) (first rows))]
    (mapv (fn [row]
            (zipmap header (map str/trim row)))
          (rest rows))))

(def glossary-rows
  "Delay so we only parse the CSV once."
  (delay
    (load-glossary-csv "glossaries/tech-glossary.csv")))


(defn glossary-by-domain
  "Return a vector of terms for the given domain (string or keyword).
   Example: (glossary-by-domain :tech) -> [\"API\" \"Kubernetes\" ...]"
  [domain]
  (let [d (if (keyword? domain) (name domain) (str domain))]
    (->> @glossary-rows
         (filter #(= d (:domain %)))
         (map :term)
         (remove str/blank?)
         distinct
         sort
         vec)))


(csv/read-csv (io/reader (io/resource "glossaries/tech-glossary.csv")))

(defn tech-glossary
  "Convenience fn: all tech terms as a vector of strings."
  []
  (glossary-by-domain "tech"))