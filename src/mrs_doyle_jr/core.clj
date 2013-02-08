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

(def tea-round-duration 30)
(def just-missed-duration 10)

(defn ppstr [o]
  (with-out-str (pprint o)))

(defn handle-errors [state exception]
  (let [stack (with-out-str (print-stack-trace exception))]
    (.println System/err (format "{{{{{{{{{{{{{{{{{{{{
Error: %s
State: %s
Stack: %s
}}}}}}}}}}}}}}}}}}}}"
                                 exception (ppstr state) stack))))

(def default-state {:people {}
                    :informed #{}
                    :drinkers #{}
                    :setting-prefs #{}
                    :tea-countdown false
                    :double-jeopardy nil
                    :last-round nil
                    :actions []})

(def state (agent default-state
                  :error-mode :continue
                  :error-handler handle-errors))

(def config (atom nil))
(def at-pool (atom nil))
(def connection (atom nil))

(defn get-person [addr]
  (try (mongo/insert! :people {:_id addr
                               :newbie true
                               :askme true})
    (catch Exception e ; com.mongo/MongoException$DuplicateKey?
      (mongo/fetch-one :people :_id addr))))

(defn ensure-person [state addr]
  (if (get-in state [:people addr])
    state
    (assoc-in state [:people addr] (get-person addr))))

(defn update-person [state addr k v]
  (assoc-in state [:people addr]
            (mongo/fetch-and-modify :people {:_id addr}
                                    {:$set {k v}}
                                    :return-new? true)))

(defn get-salutation [jid]
  (s/replace
    (s/replace
      (first (s/split jid #"@"))
      "."
      " ")
    #"^[a-z]| [a-z]"
    #(.toUpperCase %)))

(defn build-well-volunteered-message [maker prefs]
  (reduce str
          (conv/well-volunteered)
          (map #(str "\n * " (get-salutation %) " (" (prefs %) ")")
               (filter (partial not= maker) (keys prefs)))))

(defn message-action [addr text]
  #(jabber/send-message % addr text))

(defn presence-action [addr status]
  #(presence/set-availability! % :available status addr))

(defn subscribe-action [addr]
  #(presence/subscribe-presence % addr))

(defn process-actions [state conn]
  (doseq [action (:actions state)]
    (action conn))
  (assoc state :actions []))

(defn tea-round-actions [maker prefs]
  (map #(message-action % (if (nil? maker)
                            (conv/on-your-own)
                            (if (= % maker)
                              (build-well-volunteered-message maker prefs)
                              (conv/other-offered-arg (get-salutation maker)))))
       (keys prefs)))

(defn select-by-weight [options weights]
  (let [cum   (reductions + weights)
        total (last cum)
        r     (rand total)]
    (first (first (drop-while #(< (last %) r)
                              (map vector options cum))))))

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
  (let [drinkers (:drinkers state)
        ; When all drinkers are newbies, the first will always be chosen,
        ; so shuffle drinkers to make sure it's random.
        maker (select-tea-maker (:double-jeopardy state)
                                (shuffle drinkers))
        prefs (zipmap drinkers
                      (map #(get-in state [:people % :teaprefs])
                           drinkers))]
    (println "Tea's up! " maker prefs)
    (when maker
      (stats/update-stats maker drinkers))
    (-> state
        (update-in [:actions] (comp vec concat) (tea-round-actions maker prefs))
        (assoc :double-jeopardy (or maker (:double-jeopardy state)))
        (assoc :tea-countdown false)
        (assoc :drinkers #{})
        (assoc :informed #{})
        (assoc :setting-prefs #{})
        (assoc :last-round (at/now)))))

(defn handle-tea-round [conn]
  (send state #(-> %
                   (process-tea-round)
                   (process-actions conn))))

(defn tea-queries [state conn]
  (let [candidates (filter #(and (get-in state [:people % :askme])
                                 (not (get-in state [:informed %])))
                           (jabber/available conn))]
    (doseq [addr candidates]
      (jabber/send-message conn addr (conv/want-tea)))
    (update-in state [:informed] union candidates)))

(defn tea-countdown-action []
  (fn [conn]
    (send state tea-queries conn)
    (at/after (* 1000 tea-round-duration)
              (partial handle-tea-round conn)
              @at-pool)))

(defn provided-prefs [state addr text]
  (let [clauses (s/split text #", *" 2)]
    (when (and (> (count clauses) 1)
               (re-find conv/trigger-tea-prefs (last clauses)))
      (update-person state addr :teaprefs (last clauses)))))

(defn ask-for-prefs [state addr]
  (when (not (get-in state [:people addr :teaprefs]))
    (-> state
        (update-in [:setting-prefs] conj addr)
        (update-in [:actions] conj (message-action addr (conv/how-to-take-it))))))

(defn how-they-like-it-clause [state from text]
  (or (provided-prefs state from text)
      (ask-for-prefs state from)
      state))

(defn presence-message [askme addr]
  (if askme
    ""
    (conv/alone-status-arg (get-salutation addr))))

(defn in-round [state addr]
  (if (and (:tea-countdown state)
           (get-in state [:people addr :askme])
           (not (get-in state [:informed addr])))
    (-> state
        (update-in [:informed] conj addr)
        (update-in [:actions] conj (message-action addr (conv/want-tea))))
    state))

(defn set-askme [state addr]
  (update-person state addr :askme true))

(defn message-dbg [state from text]
  (when (= "dbg" text)
    (update-in state [:actions] conj (message-action from (ppstr state)))))

(defn message-rude [state from text]
  (when (re-find conv/trigger-rude text)
    (update-in state [:actions] conj (message-action from (conv/rude)))))

(defn message-go-away [state from text]
  (when (re-find conv/trigger-go-away text)
    (-> state
        (update-person from :askme false)
        (update-in [:actions] conj
                   (presence-action from (presence-message false from))
                   (message-action from (conv/no-tea-today))))))

(defn message-setting-prefs [state from text]
  (when (get-in state [:setting-prefs from])
    (-> state
        (update-person from :teaprefs text)
        (update-in [:setting-prefs] disj from)
        (update-in [:actions] conj
                   (message-action from (conv/ok))))))

(defn message-drinker [state from text]
  (when (and (:tea-countdown state)
             (get-in state [:drinkers from]))
    (cond
     (re-find conv/trigger-tea-prefs text)
     (-> state
         (update-person from :teaprefs text)
         (update-in [:actions] conj (message-action from (conv/like-tea-arg text))))

     (re-find conv/trigger-yes text)
     (update-in state [:actions] conj (message-action from (conv/ok)))

     :else
     (update-in state [:actions] conj (message-action from (conv/no-backout))))))

(defn message-countdown [state from text]
  (when (:tea-countdown state)
    (if (re-find conv/trigger-yes text)
      (-> state
          (update-in [:drinkers] conj from)
          (update-in [:actions] conj (message-action from (conv/ah-grand)))
          (how-they-like-it-clause from text))
      (update-in state [:actions] conj (message-action from (conv/ah-go-on))))))

(defn message-add-person [state from text]
  (when-let [other (re-find conv/trigger-add-person text)]
    (update-in state [:actions] conj
               (subscribe-action other)
               (message-action from (conv/add-person)))))

(defn message-tea [state from text]
  (when (re-find conv/trigger-tea text)
    (-> state
        (assoc :tea-countdown true)
        (update-in [:drinkers] conj from)
        (update-in [:informed] conj from)
        (update-in [:actions] conj
                   (message-action from (conv/good-idea))
                   (tea-countdown-action))
        (how-they-like-it-clause from text))))

(defn message-hello [state from text]
  (when (re-find conv/trigger-hello text)
    (update-in state [:actions] conj (message-action from (conv/greeting)))))

(defn message-yes [state from text]
  (when (re-find conv/trigger-yes text)
    (update-in state [:actions] conj
               (message-action from (if (< (- (at/now) (or (:last-round state) 0))
                                           (* 1000 just-missed-duration))
                                      (conv/just-missed)
                                      (conv/huh))))))

(defn message-huh [state from text]
  (update-in state [:actions] conj
             (message-action from (conv/huh))))

(defn handle-message [conn msg]
  (println "Message: " (:from msg) ":" (:body msg))
  (send state in-round (:from msg))
  (send state set-askme (:from msg))
  (send state
        #(or
          (message-dbg           %1 %2 %3)
          (message-rude          %1 %2 %3)
          (message-go-away       %1 %2 %3)
          (message-setting-prefs %1 %2 %3)
          (message-drinker       %1 %2 %3)
          (message-countdown     %1 %2 %3)
          (message-add-person    %1 %2 %3)
          (message-tea           %1 %2 %3)
          (message-hello         %1 %2 %3)
          (message-yes           %1 %2 %3)
          (message-huh           %1 %2 %3)
          (identity              %1))
        (:from msg)
        (:body msg))
  (send state process-actions conn)
  nil)

(defn presence-status [state addr]
  (update-in state [:actions] conj
             (presence-action addr
                              (presence-message (get-in state [:people addr :askme])
                                                addr))))

(defn presence-newbie [state addr]
  (if (get-in state [:people addr :newbie])
    (-> state
        (update-person addr :newbie false)
        (update-in [:actions] conj
                   (message-action addr (conv/newbie-greeting))))
    state))

(defn handle-presence [presence]
  (println "Presence: " presence)
  (let [addr (:jid presence)]
    (send state presence-status addr)
    (when (and (:online? presence)
               (not (:away? presence)))
      (send state presence-newbie addr)
      (send state in-round addr))
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
    (doseq [addr (jabber/available conn)]
      (send state #(ensure-person % addr)))
    conn))

(defn -main []
  (load-config "config.dat")
  (make-at-pool)
  (connect-mongo (:mongo @config))
  (connect-jabber (:jabber @config))
  (while (.isConnected @connection)
    (Thread/sleep 100)))
