(ns mrs-doyle-jr.conversation-test
  (:require [mrs-doyle-jr.conversation :refer :all]
            [midje.sweet :refer :all :exclude [one-of]]))

(facts "about conversation"

       (fact "responds"
             (let [pred (respond-to "a" "b")]
               (pred "b") => truthy
               (pred "a c") => truthy
               (pred "ab") => falsey)
             (let [pred (respond-to "^a" "b$")]
               (pred "a c") => truthy
               (pred "c b") => truthy
               (pred "b a") => falsey
               (pred "ab") => falsey)
             (hello? "Good morning!") => truthy
             (hello? "Goodbye") => falsey
             (add-person? "Mrs.Doyle@swiftkey.net") => truthy
             (gordon? "What is \"tea\"") => truthy
             (rude? "I like coffee") => truthy)

       (fact "randomizes text"
             (let [options ["zero" "one" "two"]
                   func (apply one-of options)]
               (func) => (partial contains? (set options))))

       (fact "randomizes with arg"
             (let [options ["111 %s 222" "333 %s 444"]
                   func (apply one-of-arg options)
                   arg "000"]
               (func arg) => (partial contains #{"111 000 222" "333 000 444"}))))
