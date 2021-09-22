(ns druk-redux-todo.core
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [clerk.core :as clerk]))

;; -------------------------
;;; Logic

(defonce event-store (r/atom []))

(defn dispatch!
  [event]
  (r/rswap! event-store conj event))

(defn todo-reducer
  [{:keys [events] :as state}]
  (reduce
   (fn [{:keys [_ todo] :as state} {:keys [type text] :as action}]
     (case type
       :add-item
       (let [idx (inc (:idx todo))
             present (get-in state [:todo :present])]
         (as-> state $
           (assoc-in $ [:todo :idx] idx)
           (cond-> $
             (not-empty present)
             (update-in [:todo :past] conj present))

           (update-in $ [:todo :present] conj {:idx idx :text text})

           (update-in $ [:todo :future] empty)))

       :delete-item
       (let [present (get-in state [:todo :present])]
         (as-> state $
           (cond-> $
             (not-empty present)
             (update-in [:todo :past] conj present))

           (update-in $ [:todo :present]
                      (fn [todos]
                        (vec (remove #(= (:idx %) (:idx action)) todos))))

           (update-in $ [:todo :future] empty)))

       :undo
       (let [present (get-in state [:todo :present])
             past (get-in state [:todo :past])]
         (cond-> state
           (not-empty present)
           (update-in [:todo :future] conj present)

           :unconditionally
           (assoc-in [:todo :present] (or (peek past) []))

           (not-empty past)
           (update-in [:todo :past] pop)))

       :redo
       (let [past (get-in state [:todo :past])
             present (get-in state [:todo :present])
             future (get-in state [:todo :future])]
         (cond-> state
           (not (= present (last past)))
           (update-in [:todo :past] conj present)

           (not-empty future)
           (assoc-in [:todo :present] (peek future))

           (not-empty future)
           (update-in [:todo :future] pop)))))

   state events))

;; Todo elements generation
(defn todo-list
  [event-store]
  (-> {:events event-store
       :todo {:idx 0
              :past []
              :present []
              :future '()}}
      todo-reducer
      (get-in [:todo :present])))

;; -------------------------
;; Page components

(def input-val (r/atom ""))

(defn todo-input []
  [:input.todo-input
   {:type "text"
    :placeholder "What to do...what to do?"
    :on-key-down #(case (.-which %)
                    13 (do (dispatch! {:type :add-item
                                       :text @input-val})
                           (reset! input-val "")))
    :value @input-val
    :on-change #(reset! input-val (.. % -target -value))
    :style {:width "100%"
            :padding "5px"
            :font-size "20px"}}])

(defn add-item-button []
  [:button.button
   {:type "submit"
    :style {:padding-bottom "5px"}
    :on-click (fn []
                (dispatch! {:type :add-item
                            :text @input-val})
                (reset! input-val ""))}
   "＋"])

(defn undo-button []
  [:button.button
   {:style {:margin-right "10px"}
    :on-click #(dispatch! {:type :undo})}
   "⭯"])

(defn redo-button []
  [:button.button
   {:on-click #(dispatch! {:type :redo})}
   "⭮"])

(defn delete-item [id]
  [:div.delete-item
   {:on-click #(dispatch! {:type :delete-item :idx id})}
   [:button "✗"]])

(defn todo-item
  [{:keys [id text]}]
  [:div.todo-item
   {:id id
    :style {:display "flex"
            :justify-content "space-between"}}
   [:div.item-content
    [:span
     {:style {:font-style "italic"
              :color "gray"}} (str id ") ")]
    [:span text]]
   [delete-item id]])

(defn home-page []
  (let [todo-list @(r/track! todo-list @event-store)]
    [:div#main
     [:h1 {:style {:text-align "center"}}
      "Redux Todo List"]
     [:div.buttons
      {:style {:margin-top "20px"
               :margin-bottom "20px"
               :display "flex"
               :justify-content "space-around"}}
      [undo-button]
      [add-item-button]
      [redo-button]]
     [:div.todo-input
      [todo-input]]
     [:div.todo-items
      {:style {:margin-top "20px"}}]
     (for [{:keys [idx text]} todo-list]
       ^{:key idx}
       [todo-item {:id idx :text text}])]))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [home-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (mount-root))
