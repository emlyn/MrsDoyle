(ns mrs-doyle-jr.core
  (:require
   [mrs-doyle-jr.conversation :as conv]
   [quit-yo-jibber :as jabber]
   [quit-yo-jibber.presence :as presence]
   [overtone.at-at :as at]
   [clojure.string :as s]
   [clojure.set :refer [union]]
   [clojure.stacktrace :refer [print-stack-trace]]
   [clojure.pprint :refer [pprint]]
   [somnium.congomongo :as mongo]))

; Ask if they want tea - make sure initiator is not asked
; in-round - make answer after others, why does she reply 'huh' to hello
; TODO: Stats! (congomongo)
; TODO: Select maker according to stats

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

(def connection (atom nil))
(def at-pool (atom nil))

(defn new-person [addr]
  {:jid addr
   :newbie true
   :askme true})

(defn ensure-person [state addr]
  (if (get-in state [:people addr])
    state
    (assoc-in state [:people addr] (new-person addr))))

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

(defn tea-actions [state maker]
  (let [drinkers (:drinkers state)
        prefs (zipmap drinkers
                      (map (comp :teaprefs (:people state))
                           drinkers))]
    (update-in state [:actions] (comp vec concat)
               (map #(message-action %
                                     (if (nil? maker)
                                       (conv/on-your-own)
                                       (if (= maker %)
                                         (build-well-volunteered-message maker prefs)
                                         (conv/other-offered-par (get-salutation maker)))))
                drinkers))))

(defn select-tea-maker [dj drinkers]
  (rand-nth (filter (partial not= dj) drinkers)))

(defn process-tea-round [state]
  (let [maker (when (> (count (:drinkers state))
                       1)
                (select-tea-maker (:double-jeopardy state)
                                  (:drinkers state)))]
    (-> state
        (tea-actions maker)
        (assoc :double-jeopardy (or maker (:double-jeopardy state)))
        (assoc :tea-countdown false)
        (assoc :drinkers #{})
        (assoc :informed #{})
        (assoc :setting-prefs #{})
        (assoc :last-round (at/now)))))

(defn process-actions [state conn]
  (doseq [action (:actions state)]
    (action conn))
  (assoc state :actions []))

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

(defn tea-action []
  (fn [conn]
    (send state tea-queries conn)
    (at/after (* 1000 tea-round-duration)
              (partial handle-tea-round conn)
              @at-pool)))

(defn how-they-like-it-clause [state from text]
  (let [clauses (s/split text #", *" 2)]
    (-> state
        (#(if (and (> (count clauses) 1)
                   (re-find conv/trigger-tea-prefs (last clauses)))
            (assoc-in % [:people from :teaprefs] (last clauses))
            %))
        (#(if (not (get-in % [:people from :teaprefs]))
            (-> %
                (update-in [:setting-prefs] conj from)
                (update-in [:actions] conj (message-action from (conv/how-to-take-it))))
            %)))))

(defn presence-message [askme addr]
  (if askme
    ""
    (conv/alone-status-par (get-salutation addr))))

(defn in-round [state addr]
  (if (and (:tea-countdown state)
           (get-in state [:people addr :askme])
           (not (get-in state [:informed addr])))
    (-> state
        (update-in [:informed] conj addr)
        (update-in [:actions] conj (message-action addr (conv/want-tea))))
    state))

(defn set-askme [state addr]
  (assoc-in state [:people addr :askme] true))

(defn message-dbg [state from text]
  (when (= "dbg" text)
    (update-in state [:actions] conj (message-action from (ppstr state)))))

(defn message-rude [state from text]
  (when (re-find conv/trigger-rude text)
    (update-in state [:actions] conj (message-action from (conv/rude)))))

(defn message-go-away [state from text]
  (when (re-find conv/trigger-go-away text)
    (-> state
        (assoc-in [:people from :askme] false)
        (update-in [:actions] conj
                   (presence-action from (presence-message false from))
                   (message-action from (conv/no-tea-today))))))

(defn message-setting-prefs [state from text]
  (when (get-in state [:setting-prefs from])
    (-> state
        (assoc-in  [:people from :teaprefs] text)
        (update-in [:setting-prefs] disj from)
        (update-in [:actions] conj
                   (message-action from (conv/ok))))))

(defn message-drinker [state from text]
  (when (and (:tea-countdown state)
             (get-in state [:drinkers from]))
    (cond
     (re-find conv/trigger-tea-prefs text)
     (-> state
         (assoc-in [:people from :teaprefs] text)
         (update-in [:actions] conj (message-action from (conv/like-tea-par text))))

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
                   (tea-action))
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
  (await state)
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
        (assoc-in [:people addr :newbie] false)
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

(defn- get-connection-info []
  (read-string (slurp "login.txt")))

(defn make-at-pool []
  (swap! at-pool (constantly (at/mk-pool))))

(defn make-connection []
  (let [conn (jabber/make-connection
              (get-connection-info)
              (var handle-message))]
    (swap! connection (constantly conn))
    (presence/add-presence-listener conn (var handle-presence))
    (doseq [addr (jabber/roster conn)]
      (send state #(ensure-person % addr)))
    conn))

(defn -main []
  (make-at-pool)
  (make-connection)
  (read-line))
