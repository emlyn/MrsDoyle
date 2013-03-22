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

(defn get-drinker-luck [& {:keys [only limit]}]
  (let [match (or ({:lucky {:$gt 1.0}
                    :unlucky {:$lt 1.0}} only)
                  {:$gt 0.0})
        r (mongo/aggregate :people
                           {:$match {:made  {:$gt 0}
                                     :drunk {:$gt 0}}}
                           {:$project {:_id :$_id
                                       :drunk :$drunk
                                       :ratio {:$divide [:$drunk :$made]}}}
                           {:$match {:ratio match}}
                           {:$sort (array-map :ratio (if (= only :unlucky) 1 -1)
                                              :drunk -1)}
                           {:$limit (or limit 1000000)})]
    (map (fn [d] [(:_id d) (:ratio d)])
         (:result r))))

(defn get-recent-drinkers []
  (let [r (mongo/aggregate :rounds
                           {:$match {:date {:$gt (year-ago)}}}
                           {:$group {:_id {:y {:$year :$date}
                                           :m {:$month :$date}
                                           :d {:$dayOfMonth :$date}}
                                     :cups {:$sum :$cups}
                                     :rounds {:$sum 1}}})
        q (mongo/distinct-values :cups "date"
                                 :where {:date {:$gt (year-ago)}})]
    (map (fn [d] [(map #(% (:_id d)) [:y :m :d])
                 (:rounds d)
                 (:cups d)])
         (:result r))))
