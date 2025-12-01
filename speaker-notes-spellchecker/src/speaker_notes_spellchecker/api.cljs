(ns speaker-notes-spellchecker.api)

(defn backend-base-url []
  ;; If running locally via shadow-cljs (localhost:8080), use local backend
  ;; Otherwise, in Docker use the backend service hostname
  (if (= "localhost" js/location.hostname)
    "http://localhost:3000"
    "http://backend:3000"))

(defn spellcheck-url []
  (str (backend-base-url) "/api/spellcheck"))