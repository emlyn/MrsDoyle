(ns mrs-doyle-jr.core
  (:require
   [mrs-doyle-jr.conversation :as convo]
   [clojure.data.codec.base64 :as b64]
   [quit-yo-jibber :as jabber]
   [quit-yo-jibber.presence :as presence]
   [overtone.at-at :as at]
   [clojure.string :as s]
   [somnium.congomongo :as mongo]
   ))

; Fix tea round
; TODO: Stats! (congomongo)
; TODO: Select maker according to stats

(def tea-round-duration 30)
(def just-missed-duration 10)

(def connection (atom nil))
(def roster (atom {}))

(def informed (ref #{}))
(def drinkers (ref #{}))
(def setting-prefs (ref #{}))
(def tea-countdown (ref false))
(def double-jeopardy (ref ""))
(def last-round (ref (at/now)))

(def at-pool (at/mk-pool))

(defn- ^{:testable true} re-from-b64 [b64string]
  (re-pattern (String. (b64/decode (.getBytes b64string)))))

(defn- ^{:testable true} random-text [options]
  (nth options
       (int (rand (count options)))))

(defn- ^{:testable true} get-person [addr]
  ((swap! roster #(if (contains? % addr)
                   %
                   (assoc %
                     addr
                     (ref {:jid addr :askme false :newbie true}))))
    addr))

(defn- ^{:testable true} get-salutation [jid]
  (s/replace
    (s/replace
      (first (s/split jid #"@"))
      "."
      " ")
    #"^[a-z]| [a-z]"
    #(.toUpperCase %)))

(defn- ^{:testable true} how-they-like-it-clause [conn text talker]
  (let [addr (@talker :jid)
        clauses (s/split text #", *" 2)]
    (when (> (count clauses) 1)
      (when (re-find convo/trigger-tea-prefs (last clauses))
        (dosync (commute talker assoc :teaprefs (last clauses)))))
    (when-not (:teaprefs @talker)
      ;(jabber/send-message conn addr (random-text convo/how-to-take-it))
      (dosync (commute setting-prefs conj addr))
      (random-text convo/how-to-take-it))))

(defn- ^{:testable true} select-tea-maker [people]
  (random-text people))

(defn- ^{:testable true} build-well-volunteered-message [maker drinkers]
  (reduce str
          (random-text convo/well-volunteered)
          (map #(str "\n * " (get-salutation %) " (" (:teaprefs @(get-person %)) ")")
               drinkers)))

(defn- ^{:testable true} presence-message [person]
  (if (:askme person)
    ""
    (format convo/alone-status (get-salutation (:jid person)))))

(defn- ^{:testable true} process-tea-round [conn]
  (println (format "Tea round up!\nInformed:%s\nDrinkers:%s\nPref:%s"
                   (s/join ", " @informed)
                   (s/join ", " @drinkers)
                   (s/join ", " @setting-prefs)))
  (cond
   (= 1 (count @drinkers)) (jabber/send-message conn (first @drinkers) (random-text convo/on-your-own))
   (< 1 (count @drinkers)) (let [teamaker (select-tea-maker (filter (partial not= @double-jeopardy) @drinkers))]
                             (println (str "Dbl Jeopardy: " @double-jeopardy))
                             (println (str "Tea maker: " teamaker))
                             (dosync (ref-set double-jeopardy teamaker))
                             (doseq [person @drinkers]
                               (jabber/send-message conn
                                                    person
                                                    (if (= teamaker person)
                                                      (build-well-volunteered-message teamaker @drinkers)
                                                      (str (get-salutation teamaker)
                                                           (random-text convo/other-offered)))))))
  (dosync
   (ref-set tea-countdown false)
   (ref-set drinkers #{})
   (ref-set informed #{})
   (ref-set setting-prefs #{})
   (ref-set last-round (at/now))))

(defn- ^{:testable true} handle-message [conn msg]
  (let [text (:body msg)
        from-addr (first (s/split (:from msg) #"/"))
        talker (get-person from-addr)]
    (println (str "Message: " from-addr ": " text))
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
      #"^dbg$" (format "###### Debug ######
Countdown: %s
Drinkers: %s
Informed: %s
SetPrefs: %s
Roster: %s"
                       @tea-countdown @drinkers @informed @setting-prefs
                       (apply str (map #(str "\n" (% 0) ": " @(% 1)) @roster)))

      ; Mrs Doyle takes no crap.
      (re-from-b64 convo/trigger-rude) (random-text convo/rude)

      ; And sometimes people take no crap.
      convo/trigger-go-away (do
                              (dosync (commute talker dissoc :askme))
                              (presence/set-availability! conn :available (presence-message @talker) from-addr)
                              (random-text convo/no-tea-today))

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
                (how-they-like-it-clause conn text talker))

           :else (random-text convo/ah-go-on))

       (re-find convo/trigger-add-person text) (do
                                                 (presence/subscribe-presence conn (re-find convo/trigger-add-person text))
                                                 (random-text convo/add-person))

       (re-find convo/trigger-tea text) (do
                                          (jabber/send-message conn from-addr (random-text convo/good-idea))
                                          (dosync
                                           (commute drinkers conj from-addr)
                                           (commute informed conj from-addr))
                                          (doseq [addr (jabber/available conn)]
                                            (let [person (get-person addr)]
                                              (println (format "Person: %s, ask: %s" addr (:askme @person)))
                                              (when (and (:askme @person)
                                                         (not= from-addr (:jid @person)))
                                                (jabber/send-message conn addr (random-text convo/want-tea))
                                                (dosync (commute informed conj addr)))))
                                          (at/after (* 1000 tea-round-duration) (partial process-tea-round conn) at-pool)
                                          (dosync (ref-set tea-countdown true))
                                          (how-they-like-it-clause conn text talker))

       (re-find convo/trigger-hello text) (random-text convo/greeting)

       (re-find convo/trigger-yes text) (if (< (- (at/now) last-round) (* 1000 just-missed-duration))
                                          (random-text convo/just-missed)
                                          (random-text convo/huh))

       :else (random-text convo/huh)))))

(defn- ^{:testable true} presence-listener [presence]
  (println (str "Presence: " presence))
  (let [addr (:jid presence)
        person (get-person addr)]
    (presence/set-availability! @connection :available (presence-message @person) addr)
    (when (and (:online? presence)
               (not (:away? presence)))
      (when (:newbie @person)
        (jabber/send-message @connection addr convo/newbie-greeting)
        (dosync (commute person dissoc :newbie)))
      (when (and @tea-countdown
                 (:askme @person)
                 (not (@informed addr)))
        (jabber/send-message @connection addr (random-text convo/want-tea))
        (dosync (commute informed conj addr)))
      )))

(defn- get-connection-info []
  (read-string (slurp "login.txt")))

(defn make-connection []
  (let [conn (jabber/make-connection
              (get-connection-info)
              (var handle-message))]
    (swap! connection (constantly conn))
    (presence/add-presence-listener conn presence-listener)
    (doseq [addr (jabber/roster conn)]
      (get-person addr))
    conn))

(defn -main []
  (make-connection)
  (read-line))
