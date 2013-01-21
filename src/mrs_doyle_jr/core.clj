(ns mrs-doyle-jr.core
  (:require
   [mrs-doyle-jr.conversation :as convo]
   [clojure.data.codec.base64 :as b64]
   [quit-yo-jibber :as jabber]
   [quit-yo-jibber.presence :as presence]
   [overtone.at-at :as at])
  (:import
   [org.jivesoftware.smack.packet Presence Presence$Type]))

; TODO: Presence listener
; TODO: Inform roster of tea round start
; TODO: Stats! (congomongo)
; TODO: Select maker according to stats

(def tea-round-duration 20)
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
                     (ref {:jid addr :askme true}))))
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

(defn select-tea-maker
  [people]
  (random-text people))

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
                               (jabber/send-message conn person (random-text (if (= teamaker person)
                                                                               convo/well-volunteered
                                                                               convo/other-offered))))))
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
    (println (str "Received from " from-addr ": " text))
    (dosync
      ; If they showed up in the middle of a round, ask them if they want tea in
      ; the normal way after we've responded to this message.
      (when (and (ensure tea-countdown)
                 (not (:askme @talker))
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
      convo/trigger-go-away (dosync
                              (commute talker dissoc :askme)
                              ;(jabber/presence conn (format convo/alone-status (get-salutation from-addr)) :available)
                              (random-text convo/no-tea-today))

      (cond
        ; See if we're expecting an answer as regards tea preferences.
        (@setting-prefs from-addr)
          (do (dosync
            (commute talker assoc :teaprefs text)
            (commute setting-prefs dissoc from-addr))
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
              (dosync
                (commute drinkers conj from-addr)
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
                                          (for [person @roster
                                                addr (@person :jid)]
                                            (when (and (@person :askme)
                                                       (not= from-addr addr))
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
  (jabber/make-connection
              (get-connection-info)
              (var handle-message)))
  ;(let [conn (jabber/make-connection
  ;            (get-connection-info)
  ;            (var handle-message))]
  ;  (presence/add-presence-listener conn presence-listener)))

(defn -main
  []
  (make-connection)
  (read-line))
