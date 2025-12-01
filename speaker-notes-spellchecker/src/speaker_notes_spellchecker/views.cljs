(ns speaker-notes-spellchecker.views
  (:require
   [re-frame.core :as rf]
   [speaker-notes-spellchecker.subs :as subs]))

(defn notes-editor []
  (let [notes @(rf/subscribe [:notes])]
    [:div.notes-editor.card
     [:div.card-header
      [:h2 "Speaker notes"]
      [:p.card-subtitle
       "Paste or type the notes you would say while presenting this slide."]]
     [:textarea.notes-textarea
      {:value notes
       :rows 12
       :on-change #(rf/dispatch [:set-notes (.. % -target -value)])
       :placeholder "E.g. \"Today I want to walk you through our cloud architecture…\""}]
     [:div.card-footer
      [:button.btn.btn-primary
       {:on-click #(rf/dispatch [:run-spellcheck])}
       "▶ Check notes"]]]))

(defn context-editor []
  (let [ctx @(rf/subscribe [:context])]
    [:div.context-editor.card
     [:div.card-header
      [:h3 "Slide context (optional)"]
      [:p.card-subtitle
       "Add slide title, bullets, or anything that helps the AI understand the context."]]
     [:textarea.context-textarea
      {:value ctx
       :rows 5
       :on-change #(rf/dispatch [:set-context (.. % -target -value)])
       :placeholder "Slide title, key bullets, audience, etc."}]]))

(defn suggestion-row [{:keys [id original candidates source status reason]}]
  [:div.suggestion-row
   {:class (str "status-" (name status))}
   [:div.suggestion-main
    [:div.suggestion-word-line
     [:span.word-chip original]
     (when (= :ai source)
       [:span.badge.badge-ai "AI"])
     (when (= :baseline source)
       [:span.badge.badge-baseline "Baseline"])]
    (when reason
      [:p.suggestion-reason reason])]
   [:div.suggestion-actions
    (for [{:keys [text confidence]} candidates]
      ^{:key (str id "-" text)}
      [:button.btn.btn-small.btn-secondary
       {:on-click #(rf/dispatch [:apply-suggestion id text])}
       text
       (when confidence
         [:span.conf (str " (" (int (* 100 confidence)) "%)")])])
    [:button.btn.btn-small.btn-ghost
     {:on-click #(rf/dispatch [:reject-suggestion id])}
     "Ignore"]]])

(defn suggestions-panel []
  (let [suggestions @(rf/subscribe [:suggestions])
        loading?    @(rf/subscribe [:loading?])
        error       @(rf/subscribe [:error])]
    [:div.suggestions-panel.card
     [:div.card-header
      [:h3 "Suggestions"]
      [:p.card-subtitle
       "Click on a suggestion to apply it to your notes, or ignore it if it’s not helpful."]]
     [:div.card-body
      (when loading?
        [:div.banner.banner-info
         "Checking notes…"])
      (when error
        [:div.banner.banner-error
         error])
      (if (seq suggestions)
        [:div.suggestions-list
         (for [s suggestions]
           ^{:key (:id s)} [suggestion-row s])]
        [:p.placeholder
         "No suggestions yet. Type some notes and click "
         [:strong "“Check notes”"]
         "."])]]))

(defn build-segments [notes suggestions]
  (let [sorted-suggs (sort-by :offset suggestions)]
    (loop [segs []
           idx  0
           [s & more] sorted-suggs]
      (if (nil? s)
        (conj segs {:type :plain
                    :text (subs notes idx (count notes))})
        (let [{:keys [offset length]} s
              before (subs notes idx offset)
              word   (subs notes offset (+ offset length))]
          (recur (cond-> segs
                   (not (empty? before))
                   (conj {:type :plain :text before})
                   true
                   (conj {:type :highlight
                          :text word
                          :suggestion s}))
                 (+ offset length)
                 more))))))

(defn notes-preview []
  (let [notes       @(rf/subscribe [:notes])
        suggestions @(rf/subscribe [:suggestions])
        segments    (build-segments notes suggestions)]
    [:div.notes-preview.card
     [:div.card-header
      [:h3 "Preview with highlights"]
      [:p.card-subtitle
       "Potential issues are highlighted inline. Hover for details."]]
     [:div.card-body.preview-box
      (if (seq notes)
        (for [{:keys [type text suggestion]} segments]
          (case type
            :plain
            ^{:key (str "plain-" (hash text))}
            [:span text]

            :highlight
            ^{:key (str "hl-" (:id suggestion))}
            [:span.word-highlight
             {:title (or (:reason suggestion) "Potential issue")}
             text]))
        [:p.placeholder "Start typing notes to see a live preview here."])]]))

(defn main-panel []
  [:div.app
   [:header.app-header
    [:h1 "AI-powered Speaker Notes Spell Checker"]
    [:p.app-tagline
     "Quickly clean up your talk track before presenting your slides."]]
   [:main.app-main
    [:section.editor-grid
     [notes-editor]
     [context-editor]]
    [:section.results-grid
     [notes-preview]
     [suggestions-panel]]]])