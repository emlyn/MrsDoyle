(ns mrs-doyle-jr.core
  (:require
   [mrs-doyle-jr.conversation :as convo]
   [clojure.data.codec.base64 :as b64]
   [quit-yo-jibber :as jabber]
   [quit-yo-jibber.presence :as presence]
   [overtone.at-at :as at])
  (:import
   [org.jivesoftware.smack.packet Presence Presence$Type]))

; TODO: Presence listener (introduce self to newbies)
; TODO: Inform roster of tea round start
; TODO: Stats! (congomongo)
; TODO: Select maker according to stats

(def tea-round-duration 30)
(def just-missed-duration 10)

(def roster (atom {}))

(def informed (ref #{}))
(def drinkers (ref #{}))
(def setting-prefs (ref #{}))
(def tea-countdown (ref false))
(def double-jeopardy (ref ""))
(def last-round (ref (at/now)))

(def at-pool (at/mk-pool))

(defn- re-from-b64
  [b64string]
  (re-pattern (String. (b64/decode (.getBytes b64string)))))

(defn- random-text
  [options]
  (nth options
       (int (rand (count options)))))

(defn- get-talker
  [addr]
  ((swap! roster #(if (contains? % addr)
                   %
                   (assoc %
                     addr
                     (ref {:jid addr :askme true :newbie true}))))
    addr))

(defn- get-salutation
  [jid]
  (clojure.string/replace
    (clojure.string/replace
      (first (clojure.string/split jid #"@"))
      "."
      " ")
    #"^[a-z]| [a-z]"
    #(.toUpperCase %)))

(defn- how-they-like-it-clause
  [conn text talker]
  (let [addr (@talker :jid)
        clauses (clojure.string/split text #", *" 2)]
    (when (> (count clauses) 1)
      (when (re-find convo/trigger-tea-prefs (clauses 1))
        (dosync (commute talker assoc :teaprefs (clauses 1)))
        nil))
    (when (not (@talker :teaprefs))
      ;(jabber/send-message conn addr (random-text convo/how-to-take-it))
      (dosync (commute setting-prefs conj addr))
      (random-text convo/how-to-take-it))))

(defn- select-tea-maker
  [people]
  (random-text people))

(defn- build-well-volunteered-message
  [maker drinkers]
  (str (random-text convo/well-volunteered)
    (apply str
      (for [drinker drinkers]
        (str "\n * " (get-salutation drinker) " (" (:teaprefs (get-talker drinker)) ")")))))

(defn- process-tea-round
  [conn]
  (println (format "Tea round up! Informed:%s Drinkers:%s Pref:%s"
                   (clojure.string/join "," @informed)
                   (clojure.string/join "," @drinkers)
                   (clojure.string/join "," @setting-prefs)))
	(cond
   (= 1 (count @drinkers)) (jabber/send-message conn (first @drinkers) (random-text convo/on-your-own))
   (> 1 (count @drinkers)) (let [teamaker (select-tea-maker (filter #(not= % double-jeopardy) @drinkers))]
                             (dosync (ref-set double-jeopardy teamaker))
                             (for [person @drinkers]
                               (jabber/send-message conn person (if (= teamaker person)
                                                                  (build-well-volunteered-message teamaker drinkers)
                                                                  (random-text convo/other-offered))))))
  (dosync
   (ref-set tea-countdown false)
   (ref-set drinkers #{})
   (ref-set informed #{})
   (ref-set setting-prefs #{})
   (ref-set last-round (at/now))))

(defn- handle-message
  [conn msg]
  (let [text (:body msg)
        from-addr ((clojure.string/split (:from msg) #"/") 0)
        talker (get-talker from-addr)]
    (println (format "*************
Received from %s: %s
Countdown: %s
Drinkers: %s
Informed: %s
SetPrefs: %s" from-addr text @tea-countdown @drinkers @informed @setting-prefs))
    (dosync
      ; If they showed up in the middle of a round, ask them if they want tea in
      ; the normal way after we've responded to this message.
      (when (and @tea-countdown
                 (:askme @talker)
                 (not (@informed from-addr)))
        (commute informed conj from-addr)
        (jabber/send-message conn from-addr (random-text convo/want-tea))
      ))
    (dosync
      (commute talker assoc :askme true))
    (condp re-find text
      ; Mrs Doyle takes no crap.
      (re-from-b64 convo/trigger-rude) (random-text convo/rude)

      ; And sometimes people take no crap.
      convo/trigger-go-away (do
                              ;(jabber/presence conn (format convo/alone-status (get-salutation from-addr)) :available)
                              (dosync (commute talker dissoc :askme))
                              (random-text convo/no-tea-today))

      ; DELETE THIS, it is dangerous and only to be used for debugging!!!
      #"^!dbg " (str (load-string (last (re-find #"^!dbg *(.*)" text))))

      (cond
        ; See if we're expecting an answer as regards tea preferences.
        (@setting-prefs from-addr)
          (dosync
            (commute talker assoc :teaprefs text)
            (commute setting-prefs disj from-addr)
            convo/ok)

        @tea-countdown
          (cond
            (@drinkers from-addr)
              (condp re-find text
                convo/trigger-tea-prefs
                  (dosync
                    (commute talker assoc :teaprefs text)
                    (format convo/like-tea text))
                convo/trigger-yes
                  convo/ok
                (random-text convo/no-backout))

            (re-find convo/trigger-yes text)
              (do
                (dosync (commute drinkers conj from-addr))
                (jabber/send-message conn from-addr (random-text convo/ah-grand))
                (how-they-like-it-clause text talker))

           :else (random-text convo/ah-go-on))
       
       (re-find convo/trigger-add-person text) (do
                                                 (presence/request-presence conn (re-find convo/trigger-add-person text))
                                                 (random-text convo/add-person))
       
       (re-find convo/trigger-tea text) (do
                                          (jabber/send-message conn from-addr (random-text convo/good-idea))
                                          (dosync
                                           (commute drinkers conj from-addr)
                                           (commute informed conj from-addr))
                                          (for [person @roster]
                                            (when (and (:askme @person)
                                                       (not= from-addr (:jid @person)))
                                              ;(jabber/send-presence addr :probe)
                                              ))
                                          (at/after (* 1000 tea-round-duration) (partial process-tea-round conn) at-pool)
                                          (dosync (ref-set tea-countdown true))
                                          (how-they-like-it-clause conn text talker))
                                          
      
       (re-find convo/trigger-hello text) (random-text convo/greeting)
      
       (re-find convo/trigger-yes text) (if (< (- (at/now) last-round) (* 1000 just-missed-duration))
                                          (random-text convo/just-missed)
                                          (random-text convo/huh))
      
       :else (random-text convo/huh)))))

(defn- presence-listener
  [& more]
  (println (str "Presence " more)))

(defn- get-connection-info
  []
  (read-string (slurp "login.txt")))

(defn make-connection
  []
  (let [conn (jabber/make-connection
              (get-connection-info)
              (var handle-message))]
    (presence/add-presence-listener conn presence-listener)))

(defn -main
  []
  (make-connection)
  (read-line))
