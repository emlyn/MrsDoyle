(ns mrs-doyle-jr.stats
  (:require
   [mrs-doyle-jr.util :refer :all]
   [somnium.congomongo :as mongo]))

(defn- stat-drinker! [round-id timestamp name initiator? cups]
  (mongo/update! :people {:_id name}
                 {:$inc {:made cups
                         :drunk 1
                         :initiated (if initiator? 1 0)}})
  (mongo/insert! :cups {:round-id round-id
                        :date timestamp
                        :drinker name
                        :initiated initiator?
                        :made cups}))

(defn- stat-round! [timestamp initiator maker cups]
  (mongo/insert! :rounds {:date timestamp
                          :initiator initiator
                          :maker maker
                          :cups cups}))

(defn log-round! [timestamp initiator maker drinkers]
  (let [cups (count drinkers)
        round-id (:_id (stat-round! timestamp initiator maker cups))]
    (doseq [drinker drinkers]
      (stat-drinker! round-id timestamp drinker
                     (= drinker initiator)
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
  (let [r (mongo/fetch :people
                       :only [:_id :drunk]
                       :sort (array-map :drunk -1
                                        :_id 1))]
    (map (fn [d] [(:_id d) (or (:drunk d) 0)])
         r)))

(defn get-maker-rounds []
  (let [r (mongo/aggregate :rounds
                           {:$group {:_id :$maker
                                     :n {:$sum 1}}}
                           {:$sort (array-map :n -1
                                              :_id 1)})]
    (map (fn [d] [(:_id d) (:n d)])
         (:result r))))

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
                                     :rounds {:$sum 1}}}
                           {:$sort (array-map :_id.y 1
                                              :_id.m 1
                                              :_id.d 1)})
        q (mongo/distinct-values :cups "date"
                                 :where {:date {:$gt (year-ago)}})]
    (map (fn [d] [(map #(% (:_id d)) [:y :m :d])
                 (:rounds d)
                 (:cups d)])
         (:result r))))

(defn get-round-sizes []
  (let [r (mongo/aggregate :rounds
                           {:$group {:_id :$cups
                                     :n {:$sum 1}}}
                           {:$sort {:_id 1}})]
    (map (fn [d] [(:_id d) (:n d)])
         (:result r))))

(defn get-cups-drunk [since people]
  ;; Only returns people who have drunk 1 or more cups.
  (let [r (mongo/aggregate :cups
                           {:$match {:date {:$gt since}
                                     :drinker {:$in people}}}
                           {:$group {:_id :$drinker
                                     :cups {:$sum 1}}})]
    (reduce #(assoc % (:_id %2) (:cups %2))
            {} (:result r))))
