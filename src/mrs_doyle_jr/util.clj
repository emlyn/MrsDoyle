(ns mrs-doyle-jr.util
  (:require [clj-time.core :refer [days years ago]]
            [clj-time.coerce :refer [to-date]]
            [clojure.string :as s]))

(defn week-ago []
  (to-date (-> 7 days ago)))

(defn year-ago []
  (to-date (-> 1 years ago)))

(defn get-salutation [addr]
  (s/replace
    (s/replace
      (first (s/split addr #"@"))
      "."
      " ")
    #"^[a-z]| [a-z]"
    #(.toUpperCase %)))
