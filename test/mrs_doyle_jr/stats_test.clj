(ns mrs-doyle-jr.stats-test
  (:require [mrs-doyle-jr.stats :refer :all]
            [somnium.congomongo :as mongo]
            [midje.sweet :refer :all]))

(facts "about stats"

       #_(fact "updates stats"
             (update-stats "polly" ["polly" "sukey" "other"]) => nil
             (provided (now) => ..now..
                       (stat-round   ..now.. "polly"  3) => nil :times 1
                       (stat-drinker ..now.. "polly"  3) => nil :times 1
                       (stat-drinker ..now.. anything 0) => nil :times 2))

       (fact "gets stats"
             (get-user-stats ["polly" "sukey" "other"]) => {"polly" [1 2]
                                                            "sukey" [3 4]
                                                            "other" [5 6]}
             (provided (mongo/fetch :people :where anything :only anything)
                       => [{:_id "polly" :drunk 1 :made 2}
                           {:_id "sukey" :drunk 3 :made 4}
                           {:_id "other" :drunk 5 :made 6}])))
