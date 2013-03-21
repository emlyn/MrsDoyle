(ns mrs-doyle-jr.stats
  (:require
   [mrs-doyle-jr.util :refer :all]
   [somnium.congomongo :as mongo]))

(defn- stat-drinker! [round-id when name cups]
  (mongo/update! :people {:_id name}
                         {:$inc {:made cups :drunk 1}})
  (mongo/insert! :cups {:round-id round-id
                        :date when
                        :drinker name
                        :made cups}))

(defn- stat-round! [when maker cups]
  (mongo/insert! :rounds {:date when
                          :maker maker
                          :cups cups}))

(defn log-round! [when maker drinkers]
  (let [cups (count drinkers)
        round-id (:_id (stat-round! when maker cups))]
    (doseq [drinker drinkers]
      (stat-drinker! round-id when drinker
                     (if (= drinker maker)
                       cups
                       0)))))

(defn get-user-stats [names]
  (let [r (mongo/fetch-by-ids :people names
                              :only [:_id :drunk :made])]
    (reduce #(assoc % (:_id %2)
                    [(or (:drunk %2) 0)
                     (or (:made %2) 0)])
            {} r)))

(defn get-drinker-cups []
  (let [r (mongo/fetch :people :only [:_id :drunk])]
    (reduce #(cons [(:_id %2) (or (:drunk %2) 0)] %)
            [] r)))

(defn get-drinker-luck []
  (let [r (mongo/fetch :people :only [:_id :drunk :made])]
    (reduce #(cons [(:_id %2) (/ (:drunk %2) (:made %2))] %)
            [] (filter #(not (zero? (or (:made %) 0))) r))))

(defn- get-cup-counts [date]
  (let [r (mongo/fetch-count :cups :where {:date date})]
    r))

(defn get-week-drinkers []
  (let [r (mongo/distinct-values :cups "date"
                                 :where {:date {:$gt (week-ago)}})]
    (reduce #(cons [(to-rfc-date %2) (get-cup-counts %2)] %) [] r)))