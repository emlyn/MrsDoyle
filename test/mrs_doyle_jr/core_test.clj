(ns mrs-doyle-jr.core-test
  (:require [mrs-doyle-jr.core :as core :refer :all]
            [mrs-doyle-jr.conversation :as convo]
            [quit-yo-jibber :as jabber]
            [quit-yo-jibber.presence :as presence]
            [overtone.at-at :as at]
            [midje.sweet :refer :all]
            [midje.util :refer [expose-testables]]))

(expose-testables mrs-doyle-jr.core)

(def default-addr "test@example.org")
(def other-addr "other@example.org")

(background
 (before :facts (dosync
                 (swap! connection (constantly nil))
                 (swap! roster (constantly {}))
                 (ref-set informed #{})
                 (ref-set drinkers #{})
                 (ref-set setting-prefs #{})
                 (ref-set tea-countdown false)
                 (ref-set double-jeopardy "")))

 (at/mk-pool) => ..at-pool..
 (rand anything) => 0.)

(defn- msg [text & [from]]
  {:body text :from (or from default-addr)})

(facts "about Mrs Doyle"
       (fact "decodes base64 regex"
             (re-from-b64 "XltKdXN0XSp0ZXN0aW5nJA==") => #"^[Just]*testing$"
             (str (re-from-b64 convo/trigger-rude)) => (contains "coffee"))

       (fact "randomizes text"
             (let [options ["zero" "one" "two"]]
               (for [t (range 3)]
                 (random-text options)) => ["two" "zero" "one"])
             (provided (rand 3) =streams=> [2.99 0.0 1.5]))

       (fact "gets people"
             @(get-person "a") => (contains {:jid "a" :newbie true})
             @(get-person "b") => (contains {:jid "b" :newbie true})
             @(get-person "a") => (contains {:jid "a" :newbie true})
             ; test that people are cached
             )

       (fact "salutes"
             (get-salutation "test@example.org") => "Test"
             (get-salutation "test.user@example.org") => "Test User")

       (fact "asks how they like it"
             (let [talker (ref {:jid default-addr})]
               ; Doesn't know how they like it, and they don't say => she asks.
               (how-they-like-it-clause ..conn.. "tea" talker) => (first convo/how-to-take-it)
               @setting-prefs => #{default-addr}

               ; If they say, she won't ask.
               (how-they-like-it-clause ..conn.. "tea, milk" talker) => nil
               (:teaprefs @talker) => "milk"

               ; If she already knows, she won't ask.
               (how-they-like-it-clause ..conn.. "tea" talker) => nil))

       (fact "selects a maker"
             (let [drinkers ["foo" "bar" "baz"]]
               (select-tea-maker drinkers) => (partial contains? (set drinkers))))

       (fact "builds well-volunteered message"
             (build-well-volunteered-message "polly" ["foo" "bar" "baz"])
             => (every-checker
                 (contains (first convo/well-volunteered))
                 (contains "Baz (milk)"))
             (provided (#'core/get-person anything) => (ref {:teaprefs "milk"})))

       (fact "doesn't like leaving people alone"
             (presence-message {:askme true  :jid ""}) => ""
             (presence-message {:askme false :jid ""}) => (contains "alone"))

       (fact "processes tea rounds"

             (fact "alone"
                   (dosync (ref-set tea-countdown true)
                           (ref-set drinkers #{default-addr}))
                   (let [on-your-own (first convo/on-your-own)]
                     (process-tea-round ..conn..) => anything
                     (provided
                      (jabber/send-message ..conn.. default-addr on-your-own) => anything))
                   @tea-countdown => false)

             (fact "not alone"
                   (dosync (ref-set tea-countdown true)
                           (ref-set drinkers #{default-addr other-addr})
                           (ref-set double-jeopardy default-addr))
                   (process-tea-round ..conn..) => anything
                   (provided
                    (jabber/send-message ..conn..
                                         default-addr
                                         (contains (first convo/other-offered)))
                    => anything
                    (jabber/send-message ..conn..
                                         other-addr
                                         (contains (first convo/well-volunteered)))
                    => anything)
                   @double-jeopardy => other-addr
                   @tea-countdown => false))

       (fact "connects"
             (let [users ["foo" "bar" "baz"]]
               (make-connection) => ..conn..
               (provided (#'core/get-connection-info) => ..conn-info..
                         (jabber/make-connection ..conn-info.. anything) => ..conn..
                         (presence/add-presence-listener ..conn.. anything) => anything
                         (jabber/roster ..conn..) => users
                         (#'core/get-person (as-checker (partial contains? (set users))))
                         => anything :times (count users))
               @connection => ..conn..))

       (facts "handles messages"
              (fact "takes no crap"
                    (handle-message ..conn.. (msg "coffee")) => (first convo/rude)
                    (:askme @(get-person default-addr)) => truthy)

              (fact "leaves people alone"
                    (handle-message ..conn.. (msg "go away")) => (first convo/no-tea-today)
                    (provided (presence/set-availability! ..conn..
                                                          :available
                                                          (as-checker (contains "alone"))
                                                          default-addr)
                              => anything)
                    (:askme @(get-person default-addr)) => falsey)

              (fact "says hello"
                    (handle-message ..conn.. (msg "hi")) => (first convo/greeting))

              (fact "adds people"
                    (handle-message ..conn.. (msg "test@example.org")) => (first convo/add-person)
                    (provided (presence/subscribe-presence ..conn.. "test@example.org")
                              => anything))

              (fact "likes tea"
                    (let [good-idea (first convo/good-idea)
                          want-tea (first convo/want-tea)]
                     (handle-message ..conn.. (msg "tea?")) => (first convo/how-to-take-it)
                     (provided
                      (jabber/send-message ..conn.. default-addr good-idea) => anything
                      (jabber/available ..conn..) => [default-addr other-addr "foo"]
                      (#'core/get-person default-addr) => (ref {:jid default-addr :askme true})
                      (#'core/get-person other-addr) => (ref {:jid other-addr :askme true})
                      (#'core/get-person "foo") => (ref {:jid "foo" :askme false})
                      (jabber/send-message ..conn.. other-addr want-tea) => anything
                      (at/after anything anything at-pool) => anything))
                    @tea-countdown => true
                    @informed => #{default-addr other-addr}
                    @setting-prefs => #{default-addr}
                    @drinkers => #{default-addr}

                    (let [ah-grand (first convo/ah-grand)]
                      (handle-message ..conn.. (msg "yes" other-addr)) => (first convo/how-to-take-it)
                      (provided
                       (jabber/send-message ..conn.. other-addr ah-grand) => anything))
                    @tea-countdown => true
                    @informed => #{default-addr other-addr}
                    @setting-prefs => #{default-addr other-addr}
                    @drinkers => #{default-addr other-addr}

                    (handle-message ..conn.. (msg "milk no sugar")) => convo/ok
                    @setting-prefs => #{other-addr}))

       (fact "handles presence"
             (fact "introduces herself"
                   (let [person (ref {:jid default-addr :askme true :newbie true})
                         presence {:jid default-addr :online? true :away? false}]
                     (swap! connection (constantly ..conn..))
                     (presence-listener presence) => anything
                     (provided
                      (#'core/get-person default-addr) => person
                      (presence/set-availability! ..conn.. :available "" default-addr) => anything
                      (jabber/send-message ..conn.. default-addr convo/newbie-greeting) => anything)
                     (:newbie @person) => falsey))

             (fact "offers tea"
                   (let [person (ref {:jid default-addr :askme true})
                         presence {:jid default-addr :online? true :away? false}
                         want-tea (first convo/want-tea)]
                     (swap! connection (constantly ..conn..))
                     (dosync (ref-set tea-countdown true))
                     (presence-listener presence) => anything
                     (provided
                      (#'core/get-person default-addr) => person
                      (presence/set-availability! ..conn.. :available "" default-addr) => anything
                      (jabber/send-message ..conn.. default-addr want-tea) => anything)
                     @informed => #{default-addr}))))
