(ns mrs-doyle-jr.web
  (:require
   [mrs-doyle-jr.stats :as stats]
   [mrs-doyle-jr.util :refer :all]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [ring.util.response :as resp]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [taoensso.timbre :refer [info]]))

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

(defn drinker-daily-cups []
  (json-response (map (fn [[dr mean max]] [(get-salutation dr) mean max])
                      (stats/get-drinker-cups-per-day))))

(defn recent-drinkers []
  (json-response (stats/get-recent-drinkers)))

(defn round-sizes []
  (json-response (stats/get-round-sizes)))

(defn weekly-stats []
  (json-response (stats/get-weekly-stats)))

(defn wrap-logger [handler]
  (fn [{:keys [request-method uri]
       :as req}]
    (let [resp (handler req)]
      (when (re-find #"[.]html$" uri)
        (info (format "From %s: %s %s"
                      (:remote-addr req)
                      (-> request-method name str/upper-case)
                      uri)))
      resp)))

(defroutes handler
  (GET "/" [] (resp/redirect "index.html"))
  (GET "/drinker-cups" [] (drinker-cups))
  (GET "/maker-rounds" [] (maker-rounds))
  (GET "/initiator-rounds" [] (initiator-rounds))
  (GET "/drinker-luck" [] (drinker-luck))
  (GET "/drinker-daily-cups" [] (drinker-daily-cups))
  (GET "/recent-drinkers" [] (recent-drinkers))
  (GET "/round-sizes" [] (round-sizes))
  (GET "/weekly-stats" [] (weekly-stats))
  (route/resources "/")
  (route/not-found "Not Found"))

(def wrapped-handler
  (-> app-routes
      wrap-logger))
