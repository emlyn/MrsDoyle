(ns mrs-doyle-jr.util
  (:require [clj-time.core :refer :all]
            [clj-time.format :refer :all]
            [clj-time.coerce :refer :all]))

(defn week-ago []
  (to-date (-> 7 days ago)))

(defn year-ago []
  (to-date (-> 1 years ago)))

(defn to-rfc-date [date]
  (unparse (formatters :rfc822) (from-date date)))
