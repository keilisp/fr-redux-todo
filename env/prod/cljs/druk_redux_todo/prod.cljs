(ns druk-redux-todo.prod
  (:require [druk-redux-todo.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
