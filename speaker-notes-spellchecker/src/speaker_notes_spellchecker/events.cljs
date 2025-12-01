(ns speaker-notes-spellchecker.events
  (:require
   [re-frame.core :as rf]
   [day8.re-frame.http-fx]
   [ajax.core :as ajax]
   [speaker-notes-spellchecker.api :as api]
   [speaker-notes-spellchecker.db :as db]))

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))
;; --- simple setters ----------------------------------------------------

(rf/reg-event-db
 :set-notes
 (fn [db [_ new-notes]]
   (assoc db :notes new-notes)))

(rf/reg-event-db
 :set-context
 (fn [db [_ new-context]]
   (assoc db :context new-context)))

;; --- spellcheck action -------------------------------------------------
;; For now we fake backend response; later you replace with an HTTP effect.

(rf/reg-event-fx
 :run-spellcheck
 (fn [{:keys [db]} _]
   (let [payload {:notes        (:notes db)
                  :slideContext (:context db)
                  :deckGlossary (:deck-glossary db)
                  :mode     :auto}]
     {:db (-> db
              (assoc :loading? true)
              (assoc :error nil))
      :http-xhrio {:method          :post
                   :uri             (api/spellcheck-url)
                   :params          payload
                   :format          (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :timeout         10000
                   :on-success      [:spellcheck-success]
                   :on-failure      [:spellcheck-failure]}})))

(rf/reg-event-db
 :spellcheck-success
 (fn [db [_ {:keys [notes corrected suggestions aiWarning]}]]
   (-> db
       (assoc :loading? false
              :error nil
              :suggestions (or suggestions []))
       (cond-> corrected (assoc :corrected corrected))
       (cond-> aiWarning (assoc :ai-warning aiWarning)))))

(rf/reg-event-db
 :spellcheck-failure
 (fn [db [_ resp]]
   (-> db
       (assoc :loading? false)
       (assoc :error (str "Spellcheck failed: "
                          (:status resp) " " (:status-text resp))))))

;; --- apply / reject suggestions ---------------------------------------

(defn apply-suggestion-to-text [text {:keys [offset length replacement]}]
  (let [before (subs text 0 offset)
        after  (subs text (+ offset length))]
    (str before replacement after)))

(rf/reg-event-db
 :apply-suggestion
 (fn [db [_ sugg-id replacement]]
   (let [suggestions (:suggestions db)
         suggestion  (first (filter #(= (:id %) sugg-id) suggestions))
         replacement (or replacement (:original suggestion))
         updated-notes (apply-suggestion-to-text (:notes db)
                                                 (assoc suggestion :replacement replacement))
         updated-suggestions (mapv (fn [s]
                                     (if (= (:id s) sugg-id)
                                       (assoc s :status :accepted
                                              :replacement replacement)
                                       s))
                                   suggestions)]
     (-> db
         (assoc :notes updated-notes)
         (assoc :suggestions updated-suggestions)))))

(rf/reg-event-db
 :reject-suggestion
 (fn [db [_ sugg-id]]
   (update db :suggestions
           (fn [suggestions]
             (mapv (fn [s]
                     (if (= (:id s) sugg-id)
                       (assoc s :status :rejected)
                       s))
                   suggestions)))))