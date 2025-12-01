(ns speaker-notes-spellchecker.db)

(def default-db
  {:name "re-frame"
   :notes ""
   :context ""
   :deck-glossary []      ;; later you can show/edit this
   :suggestions []        ;; list of suggestion maps from backend
   :loading? false
   :error nil})
