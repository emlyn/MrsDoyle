(ns mrs-doyle-jr.irc
  (:require
   [clojure.string :as s]
   [taoensso.timbre :refer [debug info warn error fatal spy]]
   [irclj.core :as ircb]))

(def connstate (atom :disconnected))
(def conn (atom nil))

(defn make-conn [conf]
  (let [conn (ircb/connect (:host conf)
                           (or (:port conf) 6667)
                           (:nick conf)
                           :username  (:user conf)
                           :real-name (:name conf)
                           :pass      (:pass conf)
                           :callbacks {})]
    (ircb/join conn (:chan conf))
    conn))

(defn ensure-conn [conf]
  (swap! conn #(if % % (make-conn conf))))

(defn appender-fn [{:keys [ap-config level prefix message more] :as args}]
  (let [conf (:irc ap-config)
        conn (ensure-conn conf)]
    (ircb/message conn (:chan conf) prefix message)))

(def appender {:doc (str "Sends messages to an IRC channel."
                         "Needs :irc config map in :shared-appender-config"
                         "e.g. {:host \"irc.freenode.net\""
                         "      :port 6667"
                         "      :pass \"seekrit\""
                         "      :name \"A Name\""
                         "      :nick \"aname\""
                         "      :channel \"#logs\"")
               :min-level :info
               :enabled?  true
               :async?    false
               :max-message-per-msecs nil
               :fn appender-fn})
