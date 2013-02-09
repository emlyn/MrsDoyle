(ns mrs-doyle-jr.conversation-test
  (:require [mrs-doyle-jr.conversation :refer :all]
            [midje.sweet :refer [fact facts contains]]))

(facts "about conversation"

       (fact "decodes base64 regex"
             (re-from-b64 "XltKdXN0XSp0ZXN0aW5nJA==") => #"^[Just]*testing$"
             (str trigger-rude) => (contains "coffee"))

       (fact "randomizes text"
             (let [options ["zero" "one" "two"]
                   func (apply one-of options)]
               (func) => (partial contains? (set options))))

       (fact "randomizes with arg"
             (let [options ["111 %s 222" "333 %s 444"]
                   func (apply one-of-arg options)
                   arg "000"]
               (func arg) => (partial contains #{"111 000 222" "333 000 444"}))))
