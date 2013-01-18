(ns mrs-doyle-jr.core
  (:require
   [mrs-doyle-jr.conversation :as convo]
   [clojure.data.codec.base64 :as b64]
   [quit-yo-jibber :as jabber]))

(def roster (atom {}))

(def informed (ref #{}))
(def drinkers (ref #{}))
(def setting-prefs (ref #{}))
(def tea-countdown (ref false))
;(def double-jeopardy (ref ""))
;(def last-round (ref ))

(defn- re-from-b64
  [b64string]
  (re-pattern (String. (b64/decode (.getBytes b64string)))))

(defn- random-text
  [options]
  (nth options
       (int (rand (count options)))))

(defn- get-talker
  [from-addr]
  ((swap! roster #(if (contains? % from-addr)
                   %
                   (assoc %
                     from-addr
                     (ref {:jid from-addr :askme true}))))
    from-addr))

(defn- get-salutation
  [jid]
  (clojure.string/replace
    (clojure.string/replace
      ((clojure.string/split jid #"@") 0)
      "."
      " ")
    #"^[a-z]| [a-z]"
    #(.toUpperCase %)))

(defn- how-they-like-it-clause
  [text talker]
  ; TODO
  (random-text convo/ah-grand))

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
          (dosync
            (commute talker assoc :teaprefs text)
            (commute setting-prefs dissoc from-addr)
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
                (how-they-like-it-clause text talker))

           :else (random-text convo/ah-go-on))
       
       (re-find convo/trigger-add-person text) "Add" ; TODO
       
       (re-find convo/trigger-tea text) "Tea!" ; TODO
      
       (re-find convo/trigger-hello text) (random-text convo/greeting)
      
       (re-find convo/trigger-yes text) (random-text convo/just-missed)
      
       :else (random-text convo/huh)))))

(defn- get-connection-info
  []
  (read-string (slurp "login.txt")))

(defn make-connection
  []
  (jabber/make-connection
    (get-connection-info)
    (var handle-message)))

(defn -main
  []
  (make-connection)
  (read-line))
