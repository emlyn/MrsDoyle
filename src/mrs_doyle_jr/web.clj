(ns mrs-doyle-jr.web
  (:require
   [mrs-doyle-jr.stats :as stats]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [ring.util.response :as resp]
   [clojure.data.json :as json]))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
      :body (json/json-str data)})

(defn drinker-cups []
  (json-response (stats/get-drinker-cups)))

(defn drinker-luck []
  (json-response (stats/get-drinker-luck)))

(defn week-drinkers []
  (json-response (stats/get-week-drinkers)))

(defroutes app-routes
  (GET "/" [] (resp/redirect "index.html"))
  (GET "/drinker-cups" [] (drinker-cups))
  (GET "/drinker-luck" [] (drinker-luck))
  (GET "/week-drinkers" [] (week-drinkers))
  (route/resources "/")
  (route/not-found "Not Found"))
