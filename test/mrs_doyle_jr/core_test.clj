(ns mrs-doyle-jr.core-test
  (:require [mrs-doyle-jr.core :as core :refer :all]
            [mrs-doyle-jr.conversation :as conv]
            [quit-yo-jibber :as jabber]
            [quit-yo-jibber.presence :as presence]
            [overtone.at-at :as at]
            [midje.sweet :refer :all]))

(background
 (before :facts
         (send state
               (constantly default-state))))

(def default-addr "test@example.org")
(def other-addr "other@example.org")

(defn- msg [text & [from]]
  {:body text :from (or from default-addr)})

(facts "about Mrs Doyle"

       #_(fact "new people"
             (new-person ..addr..) => {:jid ..addr..
                                       :newbie true
                                       :askme true})

       #_(fact "gets people"
             (:people (ensure-person default-state ..addr-a..)) => {..addr-a.. ..person-a..}
             (provided (new-person ..addr-a..) => ..person-a..)

             (:people (-> default-state
                          (ensure-person ..addr-a..)
                          (ensure-person ..addr-b..)))
             => {..addr-a.. ..person-a..
                 ..addr-b.. ..person-b..}
             (provided (new-person ..addr-a..) => ..person-a..
                       (new-person ..addr-b..) => ..person-b..)

             (:people (-> default-state
                          (ensure-person ..addr-a..)
                          (ensure-person ..addr-b..)
                          (ensure-person ..addr-a..)))
             => {..addr-a.. ..person-a..
                 ..addr-b.. ..person-b..}
             (provided (new-person ..addr-a..) => ..person-a..
                       (new-person ..addr-b..) => ..person-b..))

       (fact "salutes"
             (get-salutation "test@example.org") => "Test"
             (get-salutation "test.user@example.org") => "Test User")

       #_(fact "asks how they like it"
             (let [talker (ref {:jid default-addr})]
               ; Doesn't know how they like it, and they don't say => she asks.
               (how-they-like-it-clause ..conn.. "tea" talker) => ..how-to-take-it..
               (provided (conv/how-to-take-it) => ..how-to-take-it..)
               (:setting-prefs state) => #{default-addr}

               ; If they say, she won't ask.
               (how-they-like-it-clause ..conn.. "tea, milk" talker) => nil
               (:teaprefs @talker) => "milk"

               ; If she already knows, she won't ask.
               (how-they-like-it-clause ..conn.. "tea" talker) => nil))

       #_(fact "selects a maker"
             (let [drinkers ["foo" "bar" "baz"]]
               (select-tea-maker "bar" drinkers) => (partial contains? #{"foo" "baz"})))

       (fact "builds well-volunteered message"
             (build-well-volunteered-message "polly@example.org"
                                             {"polly@example.org" "milk"
                                              "sukey@example.org" "sugar"
                                              "other@example.org" "hot"})
             => (every-checker
                 (contains "well-volunteered")
                 (contains "Sukey: sugar")
                 (contains "Other: hot"))
             (provided (conv/well-volunteered) => "well-volunteered"))

       (fact "doesn't like leaving people alone"
             (presence-message true ..addr..) => ""
             (presence-message false default-addr) => (contains "alone"))

       #_(fact "processes tea rounds"

             (fact "alone"
                   (send state #(-> %
                                    (assoc :tea-countdown true)
                                    (assoc :drinkers #{..addr..})))

                   (process-tea-round ..conn..) => anything
                   (provided
                    (jabber/send-message ..conn.. ..addr.. (conv/on-your-own)) => anything)
                   (await state)
                   (:tea-countdown state) => false)

             (fact "not alone"
                   (send state #(-> %
                                    (assoc :tea-countdown true)
                                    (assoc :drinkers #{..addr-a.. ..addr-b..})
                                    (assoc :double-jeopardy ..addr-a..)))

                   (process-tea-round ..conn..) => anything
                   (provided
                    (jabber/send-message ..conn.. ..addr-a..
                                         (conv/other-offered-par ..addr-b..))
                    => anything
                    (jabber/send-message ..conn.. ..addr-b..
                                         (build-well-volunteered-message ..addr-b..
                                                                         anything))
                    => anything)
                   (await state)
                   (:double-jeopardy state) => ..addr-b..
                   (:tea-countdown state) => false))

       #_(fact "connects"
             (let [users ["foo" "bar" "baz"]]
               (make-connection) => ..conn..
               (provided (#'core/get-connection-info) => ..conn-info..
                         (jabber/make-connection ..conn-info.. anything) => ..conn..
                         (presence/add-presence-listener ..conn.. anything) => anything
                         (jabber/roster ..conn..) => users
                         (ensure-person (as-checker (partial contains? (set users))))
                         => anything :times (count users))
               @connection => ..conn..))

       #_(facts "handles messages"
              (fact "takes no crap"
                    (handle-message ..conn.. (msg "coffee")) => ..rude..
                    (provided (conv/rude) => ..rude..)
                    (get-in state [:person default-addr :askme]) => truthy)

              (fact "leaves people alone"
                    (handle-message ..conn.. (msg "go away")) => ..no-tea-today..
                    (provided (presence/set-availability! ..conn..
                                                          :available
                                                          (as-checker (contains "alone"))
                                                          default-addr)
                              => anything
                              (conv/no-tea-today) => ..no-tea-today..)
                    (get-in state [:person default-addr :askme]) => falsey)

              (fact "says hello"
                    (handle-message ..conn.. (msg "hi")) => ..greeting..
                    (provided (conv/greeting) => ..greeting..))

              (fact "adds people"
                    (handle-message ..conn.. (msg "test@example.org")) => ..add-person..
                    (provided (presence/subscribe-presence ..conn.. "test@example.org")
                              => anything
                              (conv/add-person) => ..add-person..))

              (fact "likes tea"
                    (handle-message ..conn.. (msg "tea?")) => ..how-to-take-it..
                    (provided
                     (jabber/send-message ..conn.. default-addr (conv/good-idea)) => anything
                     (jabber/available ..conn..) => [default-addr other-addr "foo"]
                     (ensure-person default-addr) => anything
                     (ensure-person other-addr) => anything
                     (ensure-person "foo") => anything
                     (jabber/send-message ..conn.. other-addr (conv/want-tea)) => anything
                     (at/after anything anything at-pool) => anything
                     (conv/how-to-take-it) => ..how-to-take-it..)
                    (await state)
                    (:tea-countdown state) => true
                    (:informed state) => #{default-addr other-addr}
                    (:setting-prefs state) => #{default-addr}
                    (:drinkers state) => #{default-addr}

                    (handle-message ..conn.. (msg "yes" other-addr)) => ..how-to-take-it..
                    (provided
                     (jabber/send-message ..conn.. other-addr (conv/ah-grand)) => anything
                     (conv/how-to-take-it) => ..how-to-take-it..)
                    (await state)
                    (:tea-countdown state) => true
                    (:informed state) => #{default-addr other-addr}
                    (:setting-prefs state) => #{default-addr other-addr}
                    (:drinkers state) => #{default-addr other-addr}

                    (handle-message ..conn.. (msg "milk no sugar")) => ..ok..
                    (provided (conv/ok) => ..ok..)
                    (await state)
                    (:setting-prefs state) => #{other-addr}))

       #_(fact "handles presence"
             (fact "introduces herself"
                   (let [presence {:jid default-addr :online? true :away? false}]
                     (swap! connection (constantly ..conn..))
                     (handle-presence presence) => anything
                     (provided
                      (new-person default-addr) => anything
                      (presence/set-availability! ..conn.. :available "" default-addr) => anything
                      (jabber/send-message ..conn.. default-addr (conv/newbie-greeting)) => anything)
                     (get-in state [:person default-addr :newbie]) => falsey))

             #_(fact "offers tea"
                   (let [person (ref {:jid default-addr :askme true})
                         presence {:jid default-addr :online? true :away? false}]
                     (swap! connection (constantly ..conn..))
                     (dosync (ref-set tea-countdown true))
                     (handle-presence presence) => anything
                     (provided
                      (#'core/get-person default-addr) => person
                      (presence/set-availability! ..conn.. :available "" default-addr) => anything
                      (jabber/send-message ..conn.. default-addr (conv/want-tea)) => anything)
                     (:informed state) => #{default-addr}))))
