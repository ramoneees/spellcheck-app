(ns speaker-notes-spellchecker.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
 ::name
 (fn [db]
   (:name db)))

(rf/reg-sub
 :notes
 (fn [db _]
   (:notes db)))

(rf/reg-sub
 :context
 (fn [db _]
   (:context db)))

(rf/reg-sub
 :suggestions
 (fn [db _]
   (:suggestions db)))

(rf/reg-sub
 :loading?
 (fn [db _]
   (:loading? db)))

(rf/reg-sub
 :error
 (fn [db _]
   (:error db)))