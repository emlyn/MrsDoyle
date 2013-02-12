(ns mrs-doyle-jr.core
  (:require
   [mrs-doyle-jr.conversation :as conv]
   [mrs-doyle-jr.stats :as stats]
   [quit-yo-jibber :as jabber]
   [quit-yo-jibber.presence :as presence]
   [overtone.at-at :as at]
   [clojure.string :as s]
   [clojure.set :refer [union]]
   [clojure.stacktrace :refer [print-stack-trace]]
   [clojure.pprint :refer [pprint]]
   [somnium.congomongo :as mongo]))

(defn ppstr [o]
  (with-out-str (pprint o)))

(defn log-error [state e]
  (let [trace (with-out-str (print-stack-trace e))]
    (mongo/insert! :errors {:date (java.util.Date.)
                            :exception (ppstr e)
                            :state (ppstr state)
                            :stacktrace trace})
    (.println System/err
              (format "{{{{{{{{{{{{{{{{{{{{
Error: %s
State: %s
Stack: %s
}}}}}}}}}}}}}}}}}}}}"
                      (ppstr e) (ppstr state) trace))))

(def default-state {:informed #{}
                    :drinkers #{}
                    :setting-prefs #{}
                    :tea-countdown false
                    :double-jeopardy nil
                    :last-round 0
                    :actions []})

(def state (agent default-state
                  :error-mode :continue
                  :error-handler log-error))

(def config (atom nil))
(def at-pool (atom nil))
(def connection (atom nil))

(defn get-person [addr]
  (try (mongo/insert! :people {:_id addr
                               :newbie true
                               :askme true})
       (catch com.mongodb.MongoException$DuplicateKey e
         (mongo/fetch-one :people :where {:_id addr}))))

(defn get-salutation [addr]
  (s/replace
    (s/replace
      (first (s/split addr #"@"))
      "."
      " ")
    #"^[a-z]| [a-z]"
    #(.toUpperCase %)))

(defn build-well-volunteered-message [maker prefs]
  (reduce str
          (conv/well-volunteered)
          (map #(str "\n * " (get-salutation %) " (" (prefs %) ")")
               (filter (partial not= maker) (keys prefs)))))

(defn append-actions [state & actions]
  (apply update-in state [:actions] conj actions))

(defn message-action [addr text]
  #(jabber/send-message % addr text))

(defn presence-action [addr status]
  #(presence/set-availability! % :available status addr))

(defn subscribe-action [addr]
  #(presence/subscribe-presence % addr))

(defn person-action [addr key newval]
  (fn [s] (mongo/update! :people
                        {:_id addr}
                        {:$set {key newval}})))

(defn stats-action [maker drinkers]
  (fn [s] (stats/update-stats maker drinkers)))

(defn unrecognised-action [addr text]
  (fn [s] (mongo/insert! :unrecognised {:date (java.util.Date.)
                                       :from addr
                                       :text text})
    (throw (Exception. "Testing"))))

(defn process-actions [state conn]
  (doseq [action (:actions state)]
    (try (when action
           (action conn))
         (catch Exception e
           (log-error state e))))
  (assoc-in state [:actions] []))

(defn tea-round-actions [maker prefs]
  (map #(message-action % (if (nil? maker)
                            (conv/on-your-own)
                            (if (= % maker)
                              (build-well-volunteered-message maker prefs)
                              (conv/other-offered-arg (get-salutation maker)))))
       (keys prefs)))

(defn select-by-weight [options weights]
  (let [cweight (reductions + weights)
        total   (last cweight)
        r       (rand total)]
    (if (zero? total)
      (rand-nth options)
      (first (drop-while nil?
              (map #(when (>= % r) %2)
                   cweight options))))))

(defn weight [[drunk made]]
  (/ drunk (max 1 made)))

(defn select-tea-maker [dj drinkers]
  (when (> (count drinkers) 1)
    (let [stats (stats/get-user-stats drinkers)
          weights (map #(weight (or (stats %) [0 0])) drinkers)
          maker (select-by-weight drinkers weights)]
      (println "Stats:" (map str drinkers stats weights))
      maker)))

(defn process-tea-round [state]
  (let [dj (:double-jeopardy state)
        drinkers (vec (:drinkers state))
        maker (select-tea-maker dj drinkers)
        temp (mongo/fetch :people
                          :where {:_id {:$in drinkers}}
                          :only [:_id :teaprefs])
        prefs (reduce #(assoc % (:_id %2) (:teaprefs %2))
                      {} temp)]
    (println "Tea's up!" maker drinkers prefs)
    (-> state
        (append-actions (when maker (stats-action maker drinkers)))
        (#(apply append-actions %
                 (tea-round-actions maker prefs)))
        (assoc :double-jeopardy (or maker dj))
        (assoc :tea-countdown false)
        (assoc :drinkers #{})
        (assoc :informed #{})
        (assoc :setting-prefs #{})
        (assoc :last-round (at/now)))))

(defn handle-tea-round [conn]
  (send state process-tea-round)
  (send state process-actions @connection))

(defn tea-queries [state conn]
  (let [available (jabber/available conn)
        uninformed (filter (comp not (:informed state)) available)
        candidates (mongo/fetch :people
                                :where {:_id {:$in uninformed}
                                        :askme true}
                                :only [:_id])
        ids (map :_id candidates)]
    (println "DBG" available uninformed ids)
    (doseq [addr ids]
      (jabber/send-message conn addr (conv/want-tea)))
    (update-in state [:informed] union ids)))

(defn tea-countdown-action []
  (fn [conn]
    (send state tea-queries conn)
    (at/after (* 1000 (:tea-round-duration @config 120))
              (partial handle-tea-round conn)
              @at-pool)))

(defn provided-prefs [state addr text]
  (let [clauses (s/split text #", *" 2)]
    (when (and (> (count clauses) 1)
               (re-find conv/trigger-tea-prefs (last clauses)))
      (append-actions state
                      (person-action addr :teaprefs (last clauses))))))

(defn ask-for-prefs [state person]
  (when (not (:teaprefs person))
    (-> state
        (update-in [:setting-prefs] conj (:_id person))
        (append-actions
         (message-action (:_id person) (conv/how-to-take-it))))))

(defn how-they-like-it-clause [state person text]
  (or (provided-prefs state (:_id person) text)
      (ask-for-prefs state person)
      state))

(defn presence-message [askme addr]
  (if askme
    ""
    (conv/alone-status-arg (get-salutation addr))))

(defn in-round [state person]
  (let [addr (:_id person)]
    (if (and (:tea-countdown state)
             (:askme person)
             (not (get-in state [:informed addr])))
      (-> state
          (update-in [:informed] conj addr)
          (append-actions
           (message-action addr (conv/want-tea))))
      state)))

(defn message-dbg [state person text]
  (when (= "dbg" text)
    (append-actions state
                    (message-action (:_id person) (ppstr state)))))

(defn message-rude [state person text]
  (when (re-find conv/trigger-rude text)
    (append-actions state
                    (message-action (:_id person) (conv/rude)))))

(defn message-go-away [state person text]
  (when (re-find conv/trigger-go-away text)
    (let [addr (:_id person)]
      (append-actions state
                      (person-action addr :askme false)
                      (presence-action addr (presence-message false addr))
                      (message-action addr (conv/no-tea-today))))))

(defn message-setting-prefs [state person text]
  (let [addr (:_id person)]
    (when (get-in state [:setting-prefs addr])
      (-> state
          (update-in [:setting-prefs] disj addr)
          (append-actions
           (person-action addr :teaprefs text)
           (message-action addr (conv/ok)))))))

(defn message-drinker [state person text]
  (let [addr (:_id person)]
    (when (and (:tea-countdown state)
               (get-in state [:drinkers addr]))
      (cond
       (re-find conv/trigger-tea-prefs text)
       (append-actions state
                       (person-action addr :teaprefs text)
                       (message-action addr (conv/like-tea-arg text)))

       (re-find conv/trigger-yes text)
       (append-actions state
                       (message-action addr (conv/ok)))

       :else
       (append-actions state
                       (message-action addr (conv/no-backout)))))))

(defn message-countdown [state person text]
  (when (:tea-countdown state)
    (if (re-find conv/trigger-yes text)
      (-> state
          (update-in [:drinkers] conj (:_id person))
          (append-actions
           (message-action (:_id person) (conv/ah-grand)))
          (how-they-like-it-clause person text))
      (append-actions state
                      (message-action (:_id person) (conv/ah-go-on))))))

(defn message-add-person [state person text]
  (when-let [other (re-find conv/trigger-add-person text)]
    (append-actions state
                    (subscribe-action other)
                    (message-action (:_id person) (conv/add-person)))))

(defn message-tea [state person text]
  (when (re-find conv/trigger-tea text)
    (let [addr (:_id person)]
      (-> state
          (assoc :tea-countdown true)
          (update-in [:drinkers] conj addr)
          (update-in [:informed] conj addr)
          (append-actions
           (message-action addr (conv/good-idea))
           (tea-countdown-action))
          (how-they-like-it-clause person text)))))

(defn message-hello [state person text]
  (when (re-find conv/trigger-hello text)
    (append-actions state
                    (message-action (:_id person) (conv/greeting)))))

(defn message-yes [state person text]
  (when (re-find conv/trigger-yes text)
    (append-actions state
                    (message-action (:_id person)
                                    (if (< (- (at/now)
                                              (:last-round state))
                                           (* 1000 (:just-missed-duration @config 60)))
                                      (conv/just-missed)
                                      (conv/huh))))))

(defn message-huh [state person text]
  (append-actions state
                  (message-action (:_id person) (conv/huh))
                  (unrecognised-action (:_id person) text)))

(defn handle-message [conn msg]
  (let [text (:body msg)
        addr (:from msg)
        person (get-person addr)]
    (println "Message: " addr ":" text)
    (send state append-actions
          (person-action addr :askme true))
    (send state in-round person)
    (send state #(some (fn [f] (f % person text))
                       [message-dbg
                        message-rude
                        message-go-away
                        message-setting-prefs
                        message-drinker
                        message-countdown
                        message-add-person
                        message-tea
                        message-hello
                        message-yes
                        message-huh])))
  (send state process-actions conn)
  nil)

(defn presence-status [state person]
  (append-actions state
                  (presence-action (:_id person)
                                   (presence-message (:askme person)
                                                     (:_id person)))))

(defn presence-newbie [state person]
  (if (:newbie person)
    (append-actions state
                    (message-action (:_id person) (conv/newbie-greeting))
                    (person-action (:_id person) :newbie false))
    state))

(defn handle-presence [presence]
  (println "Presence: " presence)
  (let [addr (:jid presence)
        person (get-person addr)]
    (send state presence-status person)
    (when (and (:online? presence)
               (not (:away? presence)))
      (send state presence-newbie person)
      (send state in-round person))
    (send state process-actions @connection)))

(defn load-config [fname]
  (swap! config (constantly (read-string (slurp fname)))))

(defn make-at-pool []
  (swap! at-pool (constantly (at/mk-pool))))

(defn connect-mongo [conf]
  (let [conn (mongo/make-connection (:db conf) (:args conf))]
    (mongo/set-connection! conn)))

(defn connect-jabber [conf]
  (let [conn (jabber/make-connection conf (var handle-message))]
    (swap! connection (constantly conn))
    (presence/add-presence-listener conn (var handle-presence))
    conn))

(defn -main []
  (load-config "config.dat")
  (make-at-pool)
  (connect-mongo (:mongo @config))
  (connect-jabber (:jabber @config))
  (while (.isConnected @connection)
    (Thread/sleep 100)))
