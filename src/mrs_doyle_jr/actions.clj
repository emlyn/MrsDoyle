(ns mrs-doyle-jr.actions
  (:require
   [mrs-doyle-jr.conversation :as conv]
   [mrs-doyle-jr.stats :as stats]
   [clojure.set :refer [union]]
   [quit-yo-jibber :as jabber]
   [quit-yo-jibber.presence :as presence]
   [overtone.at-at :as at]
   [somnium.congomongo :as mongo]))

(defn- send-message! [conn to msg]
  (println (format "Send (%s): %s" to msg))
  (jabber/send-message conn to msg))

(defn send-message [addr text]
  #(send-message! % addr text))

(defn send-presence [addr status]
  #(presence/set-availability! % :available status addr))

(defn subscribe [addr]
  #(presence/subscribe-presence % addr))

(defn update-person [addr key newval]
  (fn [_] (mongo/update! :people
                        {:_id addr}
                        {:$set {key newval}})))

(defn log-stats [maker drinkers]
  (let [now (java.util.Date.)]
    (fn [_] (stats/log-round! now maker drinkers))))

(defn unrecognised-text [addr text]
  (let [now (java.util.Date.)]
    (fn [conn]
      (send-message! conn addr (conv/huh))
      (mongo/insert! :unrecognised {:date now
                                    :from addr
                                    :text text}))))

(defn- tea-queries! [state conn]
  (let [available (jabber/available conn)
        uninformed (filter (comp not (:informed state)) available)
        candidates (mongo/fetch :people
                                :where {:_id {:$in uninformed}
                                        :askme true}
                                :only [:_id])
        ids (map :_id candidates)]
    (doseq [addr ids]
      (send-message! conn addr (conv/want-tea)))
    (update-in state [:informed] union (set ids))))

(defn tea-countdown [state pool duration handler]
  (fn [conn]
    (send state tea-queries! conn)
    (at/after (* 1000 duration)
              (partial handler conn)
              pool)))
