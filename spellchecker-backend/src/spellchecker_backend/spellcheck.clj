(ns spellchecker-backend.spellcheck
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [spellchecker-backend.ai :as ai])
  (:import (java.util UUID)))

;; ============================================================================
;; Tokenization helpers
;; ============================================================================

(defn tokenize-with-offsets [s]
  (let [n (count s)]
    (loop [i 0 acc []]
      (if (>= i n)
        acc
        (let [ch (.charAt s i)]
          (if (Character/isLetterOrDigit ch)
            (let [start i
                  end   (loop [j (inc i)]
                          (if (and (< j n)
                                   (Character/isLetterOrDigit (.charAt s j)))
                            (recur (inc j))
                            j))
                  token (subs s start end)]
              (recur end (conj acc {:token token
                                    :offset start
                                    :length (- end start)})))
            (recur (inc i) acc)))))))

;; ============================================================================
;; Dictionary + glossary baseline
;; ============================================================================

(defn- load-wordlist [path]
  (when-let [res (io/resource path)]
    (with-open [r (io/reader res)]
      (->> (line-seq r)
           (remove str/blank?)
           (map str/lower-case)
           set))))

(def english-dict
  (delay
    ;; If there's no external dictionary file, fall back to a tiny built-in set.
    (or (load-wordlist "dictionaries/english.txt")
        #{"we" "use" "microsoft" "azure" "for" "our" "infrastructure"
          "is" "the" "a" "to" "and" "this" "that" "of" "in" "on" "with"
          "slide" "notes" "speaker" "presentation" "today" "will" "talk"
          "about" "cloud" "architecture" "service" "services"})))

(defn glossary-set [deck-glossary]
  (set deck-glossary))

(defn suspect-token?
  [{:keys [token]} dict glossary]
  (let [lc (str/lower-case token)]
    (and
     ;; not in dictionary
     (not (contains? dict lc))
     ;; not in deck glossary (case-sensitive, assume user cares about exact form)
     (not (contains? glossary token))
     ;; not all caps acronym
     (not (re-matches #"[A-Z]{2,6}" token))
     ;; not just digits
     (not (re-matches #"[0-9]+" token)))))

(defn baseline-suspects [notes deck-glossary]
  (let [dict     @english-dict
        glossary (glossary-set deck-glossary)]
    (->> (tokenize-with-offsets notes)
         (filter #(suspect-token? % dict glossary))
         (map #(select-keys % [:token :offset :length]))
         vec)))

;; ============================================================================
;; Simple Levenshtein + candidate generation
;; ============================================================================

(defn- levenshtein [s t]
  (let [m (inc (count s))
        n (inc (count t))
        d (make-array Long/TYPE m n)]
    (dotimes [i m] (aset-long d i 0 i))
    (dotimes [j n] (aset-long d 0 j j))
    (doseq [i (range 1 m)
            j (range 1 n)]
      (aset-long d i j
                 (min
                  (inc (aget d (dec i) j))
                  (inc (aget d i (dec j)))
                  (+ (aget d (dec i) (dec j))
                     (if (= (nth s (dec i)) (nth t (dec j))) 0 1)))))
    (aget d (dec m) (dec n))))

(defn candidate-suggestions [token]
  (let [dict @english-dict
        lc   (str/lower-case token)]
    (->> dict
         ;; cheap length filter
         (filter #(<= (Math/abs ^long (- (count %) (count lc))) 2))
         (map (fn [w] [w (levenshtein lc w)]))
         ;; only consider reasonably close words
         (filter (fn [[_ dist]] (<= dist 2)))
         (sort-by second)
         (take 5)
         (map (fn [[w dist]]
                {:text w
                 :source :baseline
                 :confidence (max 0.1 (- 1.0 (* 0.3 dist)))}))
         vec)))

(defn baseline-suggestion->model
  [{:keys [token offset length]}]
  {:id         (str (UUID/randomUUID))
   :offset     offset
   :length     length
   :original   token
   :candidates (candidate-suggestions token)
   :source     :baseline
   :status     :pending
   :reason     "Unknown word according to baseline dictionary/glossary"})

;; ============================================================================
;; Build suggestions based on OpenAI results
;; ============================================================================

(defn build-suggestions-from-changes
  "Convert AI's list of {:original :replacement} into suggestion maps
   the frontend expects."
  [notes changes]
  (let [tokens (tokenize-with-offsets notes)]
    (->> changes
         (map (fn [{:keys [original replacement]}]
                ;; Find the first occurrence in the tokenized text
                (when-let [{:keys [offset length]}
                           (first (filter #(= (:token %) original) tokens))]
                  {:id         (str (UUID/randomUUID))
                   :offset     offset
                   :length     length
                   :original   original
                   :candidates [{:text replacement
                                 :source :ai
                                 :confidence 0.99}]
                   :source     :ai
                   :status     :pending
                   :reason     "AI-suggested correction"})))
         (remove nil?)
         vec)))

;; ============================================================================
;; Heuristic: decide when we actually need the LLM
;; ============================================================================

(defn llm-needed?
  "Given the original notes/context and baseline suggestions, decide whether
   this case should be escalated to the LLM.

   Heuristics:
   - Long text + no baseline suggestions -> likely grammar/clarity case -> LLM.
   - Suggestions with no or very weak candidates -> LLM.
   - Many suggestions -> LLM."
  [{:keys [notes context]} baseline-suggestions]
  (let [text-len        (count notes)
        num-suggestions (count baseline-suggestions)
        ;; suggestion has no candidates or only very low confidence ones
        has-weak-candidates?
        (some (fn [{:keys [candidates]}]
                (or (empty? candidates)
                    (every? #(< (:confidence % 0.0) 0.5) candidates)))
              baseline-suggestions)
        ;; very long odd words (likely domain-specific or ambiguous)
        has-very-long-word?
        (some (fn [{:keys [original]}]
                (> (count (str original)) 18))
              baseline-suggestions)]
    (cond
      ;; Long text, no baseline findings -> probably grammar/flow -> LLM
      (and (zero? num-suggestions)
           (> text-len 220))
      true

      ;; Any clearly weak/ambiguous candidates -> ask LLM for help
      has-weak-candidates?
      true

      ;; Lots of issues -> LLM can do a global clean-up better
      (> num-suggestions 8)
      true

      ;; Very long odd words -> LLM may understand context better
      has-very-long-word?
      true

      ;; Otherwise baseline is enough
      :else
      false)))

;; ============================================================================
;; Mode handling: baseline / ai / auto (plus legacy advanced? flag)
;; ============================================================================

(defn normalize-mode
  "Coerce legacy :advanced? boolean into a mode keyword.
   - advanced? true  -> :ai
   - advanced? false -> :baseline
   - Or accept :auto / :ai / :baseline directly in :mode."
  [{:keys [advanced? mode]}]
  (cond
    (keyword? mode) mode
    (true? advanced?) :ai
    (false? advanced?) :baseline
    :else :auto))   ;; default when not specified

;; ============================================================================
;; Helper: call AI with fallback to baseline
;; ============================================================================

(defn ai-or-baseline-response
  "Call OpenAI with the given payload, falling back to baseline suggestions if
   the AI call fails. Optionally annotates the result with :modeUsed for
   success/fallback cases."
  [{:keys [notes context deck-glossary baseline-suggestions success-mode fallback-mode]}]
  (let [payload {:notes notes
                 :context context
                 :deck-glossary deck-glossary}
        ai-res  (ai/call-openai payload)]
    (if (nil? ai-res)
      (cond-> {:notes       notes
               :suggestions baseline-suggestions
               :aiWarning   "AI unavailable, baseline used instead"}
        fallback-mode (assoc :modeUsed fallback-mode))
      (let [{:keys [corrected changes]} ai-res
            sug (build-suggestions-from-changes notes changes)]
        (cond-> {:notes       notes
                 :corrected   corrected
                 :suggestions (if (seq sug) sug baseline-suggestions)}
          success-mode (assoc :modeUsed success-mode))))))

;; ============================================================================
;; Main spellcheck entrypoint
;; ============================================================================

(defn run-spellcheck
  "opts: {:notes :context :deck-glossary :advanced? (bool legacy) :mode (:baseline | :ai | :auto)}"
  [{:keys [notes context deck-glossary mode] :as opts}]
  (println "mode " mode)
  (let [baseline-suspects    (baseline-suspects notes deck-glossary)
        baseline-suggestions (mapv baseline-suggestion->model baseline-suspects)]
    (case mode
      ;; ----------------------------------------------------------------------
      ;; 1) Baseline-only mode
      ;; ----------------------------------------------------------------------
      :baseline
      {:notes notes
       :suggestions baseline-suggestions}

      ;; ----------------------------------------------------------------------
      ;; 2) Always AI mode
      ;; ----------------------------------------------------------------------
      :ai
      (ai-or-baseline-response
       {:notes               notes
        :context             context
        :deck-glossary       deck-glossary
        :baseline-suggestions baseline-suggestions
        :success-mode        :ai
        :fallback-mode       nil})

      ;; ----------------------------------------------------------------------
      ;; 3) Auto mode: choose based on difficulty
      ;; ----------------------------------------------------------------------
      :auto
      (if (llm-needed? {:notes notes :context context} baseline-suggestions)
        ;; escalate to AI
        (ai-or-baseline-response
         {:notes               notes
          :context             context
          :deck-glossary       deck-glossary
          :baseline-suggestions baseline-suggestions
          :success-mode        :ai-auto
          :fallback-mode       :baseline-auto})
        ;; baseline is good enough
        {:notes       notes
         :suggestions baseline-suggestions
         :modeUsed    :baseline-auto}))))