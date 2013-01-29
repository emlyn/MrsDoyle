(ns mrs-doyle-jr.core
  (:require
   [mrs-doyle-jr.conversation :as conv]
   [quit-yo-jibber :as jabber]
   [quit-yo-jibber.presence :as presence]
   [overtone.at-at :as at]
   [clojure.string :as s]
   [somnium.congomongo :as mongo]))

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
      (when (re-find conv/trigger-tea-prefs (last clauses))
        (dosync (commute talker assoc :teaprefs (last clauses)))))
    (when-not (:teaprefs @talker)
      (dosync (commute setting-prefs conj addr))
      (conv/how-to-take-it))))

(defn select-tea-maker [people]
  (rand-nth people))

(defn- ^{:testable true} build-well-volunteered-message [maker drinkers]
  (reduce str
          (conv/well-volunteered)
          (map #(str "\n * " (get-salutation %) " (" (:teaprefs @(get-person %)) ")")
               drinkers)))

(defn- ^{:testable true} presence-message [person]
  (if (:askme person)
    ""
    (conv/alone-status-par (get-salutation (:jid person)))))

(defn- ^{:testable true} process-tea-round [conn]
  (println (format "Tea round up!\nInformed:%s\nDrinkers:%s\nPref:%s"
                   (s/join ", " @informed)
                   (s/join ", " @drinkers)
                   (s/join ", " @setting-prefs)))
  (cond
   (= 1 (count @drinkers)) (jabber/send-message conn (first @drinkers) (conv/on-your-own))
   (< 1 (count @drinkers)) (let [teamaker (select-tea-maker (filter (partial not= @double-jeopardy) @drinkers))]
                             (println (str "Dbl Jeopardy: " @double-jeopardy))
                             (println (str "Tea maker: " teamaker))
                             (dosync (ref-set double-jeopardy teamaker))
                             (doseq [person @drinkers]
                               (jabber/send-message conn
                                                    person
                                                    (if (= teamaker person)
                                                      (build-well-volunteered-message teamaker @drinkers)
                                                      (conv/other-offered-par (get-salutation teamaker)))))))
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
        (jabber/send-message conn from-addr (conv/want-tea))
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
      conv/trigger-rude (conv/rude)

      ; And sometimes people take no crap.
      conv/trigger-go-away (do
                             (dosync (commute talker dissoc :askme))
                             (presence/set-availability! conn :available (presence-message @talker) from-addr)
                             (conv/no-tea-today))

      (cond
        ; See if we're expecting an answer as regards tea preferences.
        (@setting-prefs from-addr)
          (dosync
            (commute talker assoc :teaprefs text)
            (commute setting-prefs disj from-addr)
            (conv/ok))

        @tea-countdown
          (cond
            (@drinkers from-addr)
              (condp re-find text
                conv/trigger-tea-prefs
                  (dosync
                    (commute talker assoc :teaprefs text)
                    (conv/like-tea-par text))
                conv/trigger-yes
                (conv/ok)
                (conv/no-backout))

            (re-find conv/trigger-yes text)
              (do
                (dosync (commute drinkers conj from-addr))
                (jabber/send-message conn from-addr (conv/ah-grand))
                (how-they-like-it-clause conn text talker))

           :else (conv/ah-go-on))

       (re-find conv/trigger-add-person text) (do
                                                 (presence/subscribe-presence conn (re-find conv/trigger-add-person text))
                                                 (conv/add-person))

       (re-find conv/trigger-tea text) (do
                                          (jabber/send-message conn from-addr (conv/good-idea))
                                          (dosync
                                           (commute drinkers conj from-addr)
                                           (commute informed conj from-addr))
                                          (doseq [addr (jabber/available conn)]
                                            (let [person (get-person addr)]
                                              (println (format "Person: %s, ask: %s" addr (:askme @person)))
                                              (when (and (:askme @person)
                                                         (not= from-addr (:jid @person)))
                                                (jabber/send-message conn addr (conv/want-tea))
                                                (dosync (commute informed conj addr)))))
                                          (at/after (* 1000 tea-round-duration) (partial process-tea-round conn) at-pool)
                                          (dosync (ref-set tea-countdown true))
                                          (how-they-like-it-clause conn text talker))

       (re-find conv/trigger-hello text) (conv/greeting)

       (re-find conv/trigger-yes text) (if (< (- (at/now) last-round) (* 1000 just-missed-duration))
                                         (conv/just-missed)
                                         (conv/huh))

       :else (conv/huh)))))

(defn- ^{:testable true} presence-listener [presence]
  (println (str "Presence: " presence))
  (let [addr (:jid presence)
        person (get-person addr)]
    (presence/set-availability! @connection :available (presence-message @person) addr)
    (when (and (:online? presence)
               (not (:away? presence)))
      (when (:newbie @person)
        (jabber/send-message @connection addr (conv/newbie-greeting))
        (dosync (commute person dissoc :newbie)))
      (when (and @tea-countdown
                 (:askme @person)
                 (not (@informed addr)))
        (jabber/send-message @connection addr (conv/want-tea))
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
