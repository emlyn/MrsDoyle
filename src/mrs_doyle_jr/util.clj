(ns mrs-doyle-jr.util
  (:require [clj-time.core :refer [today-at years ago]]
            [clj-time.coerce :refer [to-date]]
            [clojure.string :as s]))

(defn this-morning []
  (to-date (today-at 6 0)))

(defn year-ago []
  (-> 1 years ago to-date))

(defn get-salutation [addr]
  (s/replace
    (s/replace
      (first (s/split addr #"@"))
      "."
      " ")
    #"^[a-z]| [a-z]"
    #(.toUpperCase %)))

(defn join-with-and [s]
  (cond
   (empty? s) ""
   (empty? (rest s)) (first s)
   :else (str (s/join ", " (butlast s))
              " and "
              (last s))))
