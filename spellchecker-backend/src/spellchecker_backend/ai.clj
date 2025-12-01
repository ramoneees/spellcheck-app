(ns spellchecker-backend.ai
  (:require
   [clj-http.client :as http]
   [cheshire.core :as json]
   [clojure.string :as str]))

(def openai-api-key (System/getenv "OPENAI_API_KEY"))

(def openai-url "https://api.openai.com/v1/chat/completions")
(def openai-model "gpt-4.1-nano") ;; or "gpt-4.1-mini"

(defn- build-prompt
  [{:keys [notes context deck-glossary]}]
  (str
   "You are a helpful assistant that corrects spelling mistakes in speaker notes for slides.\n"
   "Return ONLY JSON, no explanations.\n\n"
   "Deck glossary terms (these should be treated as correct even if they look unusual):\n"
   (str/join ", " deck-glossary) "\n\n"
   "Speaker notes:\n"
   notes "\n\n"
   "If there are misspellings, return JSON of the form:\n"
   "{\n"
   "  \"corrected\": \"<full corrected text>\",\n"
   "  \"changes\": [\n"
   "    {\"original\": \"...\", \"replacement\": \"...\"}\n"
   "  ]\n"
   "}\n"
   "If there are no changes, just return the original text in \"corrected\" and an empty changes array."))

(defn- openai-request-body [payload]
  {:model openai-model
   :messages [{:role "user"
               :content (build-prompt payload)}]
   ;; we want deterministic-ish behavior
   :temperature 0.0})

(defn call-openai
  "Calls OpenAI and returns a map {:corrected \"...\" :changes [...]}
   or nil if anything goes wrong."
  [payload]
  (println "calling ai ")
  (when-not openai-api-key
    (throw (ex-info "OPENAI_API_KEY not set" {})))
  (try
    (let [body (json/encode (openai-request-body payload))
          resp (http/post openai-url
                          {:headers {"Authorization" (str "Bearer " openai-api-key)
                                     "Content-Type" "application/json"}
                           :body body
                           :as :json
                           :throw-exceptions false})]
      (when (= 200 (:status resp))
        (let [content (get-in resp [:body :choices 0 :message :content])]
          (try
            (json/parse-string content true)
            (catch Exception e
              (println "Failed to parse JSON from model content:" content)
              nil)))))
    (catch Exception e
      (println "OpenAI error status:")
      (println "OpenAI error body:" (.getMessage e))
      nil)))