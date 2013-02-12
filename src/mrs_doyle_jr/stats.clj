(ns mrs-doyle-jr.stats
  (:require
   [somnium.congomongo :as mongo]))

(defn now []
  (java.util.Date.))

(defn stat-drinker [when name cups]
  (mongo/update! :people {:_id name}
                         {:$inc {:made cups :drunk 1}})
  (mongo/insert! :cups {:date when
                        :drinker name
                        :made cups}))

(defn stat-round [when maker cups]
  (mongo/insert! :rounds {:date when
                          :maker maker
                          :cups cups}))

(defn update-stats [maker drinkers]
  (let [when (now)
        cups (count drinkers)]
    (stat-round when maker cups)
    (doseq [drinker drinkers]
      (stat-drinker when drinker
                    (if (= drinker maker)
                      cups
                      0)))))

(defn get-user-stats [names]
  (let [r (mongo/fetch :people
                       :where {:_id {:$in names}}
                       :only [:_id :drunk :made])]
    (reduce #(assoc % (:_id %2)
                    [(or (:drunk %2) 0)
                     (or (:made %2) 0)])
            {} r)))
