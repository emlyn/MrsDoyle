(ns mrs-doyle-jr.web
  (:require
   [mrs-doyle-jr.stats :as stats]
   [mrs-doyle-jr.util :refer :all]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [ring.util.response :as resp]
   [clojure.data.json :as json]))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
      :body (json/json-str data)})

(defn drinker-cups []
  (json-response (map (fn [[dr cs]] [(get-salutation dr) cs])
                      (stats/get-drinker-cups))))

(defn maker-rounds []
  (json-response (map (fn [[dr cs]] [(get-salutation dr) cs])
                      (stats/get-maker-rounds))))

(defn initiator-rounds []
  (json-response (map (fn [[dr cs]] [dr #_(get-salutation dr) cs])
                      (stats/get-initiator-rounds))))

(defn drinker-luck []
  (json-response (map (fn [[dr lk]] [(get-salutation dr) lk])
                      (stats/get-drinker-luck))))

(defn recent-drinkers []
  (json-response (stats/get-recent-drinkers)))

(defn round-sizes []
  (json-response (stats/get-round-sizes)))

(defn weekly-stats []
  (json-response (stats/get-weekly-stats)))

(defroutes app-routes
  (GET "/" [] (resp/redirect "index.html"))
  (GET "/drinker-cups" [] (drinker-cups))
  (GET "/maker-rounds" [] (maker-rounds))
  (GET "/initiator-rounds" [] (initiator-rounds))
  (GET "/drinker-luck" [] (drinker-luck))
  (GET "/recent-drinkers" [] (recent-drinkers))
  (GET "/round-sizes" [] (round-sizes))
  (GET "/weekly-stats" [] (weekly-stats))
  (route/resources "/")
  (route/not-found "Not Found"))
