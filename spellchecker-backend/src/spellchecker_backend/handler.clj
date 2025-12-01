(ns spellchecker-backend.handler
  (:require
   [compojure.api.sweet :refer :all]
   [ring.util.http-response :refer :all]
   [spellchecker-backend.spellcheck :as spellcheck]
   [spellchecker-backend.glossary :as glossary]
   [schema.core :as s]))

;; Define simple schemas for request and response

(s/defschema SpellcheckRequest
  {:notes s/Str
   (s/optional-key :slideContext) s/Str
   (s/optional-key :deckGlossary) [s/Str]
   (s/optional-key :mode) s/Keyword})

(s/defschema SuggestionCandidate
  {:text s/Str
   (s/optional-key :source) s/Keyword
   (s/optional-key :confidence) s/Num})

(s/defschema Suggestion
  {:id s/Str
   :offset s/Int
   :length s/Int
   :original s/Str
   (s/optional-key :candidates) [SuggestionCandidate]
   (s/optional-key :source) s/Keyword
   (s/optional-key :status) s/Keyword
   (s/optional-key :reason) s/Str})

(s/defschema SpellcheckResponse
  {:notes s/Str
   (s/optional-key :corrected) s/Str
   :suggestions [Suggestion]
   (s/optional-key :aiWarning) s/Str
   (s/optional-key :modeUsed) s/Keyword})

;; Main API with Swagger configuration
(def app
  (api
   {:swagger
    {:ui "/swagger-ui"
     :spec "/swagger.json"
     :data {:info {:title "Speaker Notes Spellchecker API"
                   :description "API for AI-assisted spell checking of speaker notes"}
            :tags [{:name "spellcheck" :description "Spellchecking operations"}]}}}

   (context "/api" []
     :tags ["spellcheck"]

     (GET "/health" []
       :summary "Health check endpoint"
       :return {:status s/Str}
       (ok {:status "ok"}))

     (POST "/spellcheck" []
       :summary "Run spellcheck on speaker notes"
       :body [body SpellcheckRequest]
       :return SpellcheckResponse
       (let [{:keys [notes slideContext deckGlossary mode]} body
             _             (println "[spellcheck-request] body=" body)
             mode          mode
             notes         (or notes "")
             ctx           (or slideContext "")
             deck-glossary (or deckGlossary glossary/tech-glossary)
             result        (spellcheck/run-spellcheck
                            {:mode mode
                             :notes notes
                             :context ctx
                             :deck-glossary deck-glossary})
             _             (println "[spellcheck-response] result=" result)]
         (ok result))))))