(defproject mrs-doyle-jr "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [quit-yo-jibber "0.4.1"]
                 [overtone/at-at "1.1.1"]
                 [congomongo "0.4.0"]
                 [jivesoftware/smack "3.1.0"]]
  :profiles {:dev
             {:dependencies [[midje "1.5-alpha5"]]
              :plugins      [[lein-midje "3.0-alpha1"]]}}
  :main mrs-doyle-jr.core)
