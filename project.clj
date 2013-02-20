(defproject mrs-doyle-jr "0.1.0"
  :description "Port of Mrs Doyle (https://AdamClements/MrsDoyle) to clojure"
  :url "https://github.com/emlyn/MrsDoyle"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [clj-base64 "0.0.2"]
                 [quit-yo-jibber "0.4.2-SNAPSHOT"]
                 [overtone/at-at "1.1.1"]
                 [congomongo "0.4.0"]
                 [jivesoftware/smack "3.1.0"]]
  :profiles {:dev
             {:dependencies [[midje "1.5-alpha10"]]
              :plugins      [[lein-midje "3.0-alpha4"]]}}
  :main mrs-doyle-jr.core)
