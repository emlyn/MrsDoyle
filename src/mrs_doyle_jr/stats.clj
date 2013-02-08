(ns mrs-doyle-jr.stats
  (:require
   [somnium.congomongo :as mongo]))

(defn- stat-drinker [now name cups]
  (mongo/update! :people {:_id name}
                         {:$inc {:made cups :drunk 1}})
  (mongo/insert! :cups {:date now
                        :drinker name
                        :made cups}))

(defn- stat-round [now maker cups]
  (mongo/insert! :rounds {:date now
                          :maker maker
                          :cups cups}))

(defn update-stats [maker drinkers]
  (let [now (java.util.Date.)
        cups (count drinkers)]
    (stat-round now maker cups)
    (doseq [drinker drinkers]
      (stat-drinker now drinker (if (= drinker maker) cups 0)))))

(defn get-user-stats [name]
  (let [r (mongo/fetch-one :people
                           :where {:_id name}
                           :only [:made :drunk])]
    [(:drunk r) (:made r)]))