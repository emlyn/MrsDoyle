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
    (into {}
          (map (fn [d] [(:_id d)
                       [(or (:drunk d) 0)
                        (or (:made d) 0)]])
               r))))

(defn get-drinker-cups []
  (let [r (mongo/fetch :people :only [:_id :drunk])]
    (sort-by (fn [[id dr]] [(- dr) id])
             (map (fn [d] [(:_id d) (or (:drunk d) 0)])
                  r))))

(defn get-drinker-luck []
  (let [r (mongo/fetch :people :only [:_id :drunk :made])]
    (sort-by (fn [[id lk]] [(- lk) id])
             (map (fn [d] [(:_id d) (/ (:drunk d) (:made d))])
                  (filter #(not (zero? (or (:made %) 0)))
                          r)))))

(defn- get-cup-counts [date]
  (mongo/fetch-count :cups
                     :where {:date date}))

(defn get-recent-drinkers []
  (let [r (mongo/distinct-values :cups "date"
                                 :where {:date {:$gt (year-ago)}})]
    (map (fn [d] [(to-rfc-date d) (get-cup-counts d)])
         r)))
