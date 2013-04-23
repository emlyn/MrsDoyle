(ns mrs-doyle-jr.conversation
  (:require [clojure.string :refer [join split]]
            [remvee.base64 :refer [decode-str]]))

(defn one-of [& statements]
  (fn [] (rand-nth statements)))

(defn one-of-arg [& statements]
  (fn [arg] (format (rand-nth statements) arg)))

(defn respond-to [& patterns]
  (fn [s] (re-find
          (re-pattern
           (str "(?i)"
                (join "|"
                      (map #(str (when-not (.startsWith % "^")
                                   "\\b")
                                 %
                                 (when-not (or (.endsWith % "$")
                                               (.endsWith % "\""))
                                   "\\b"))
                           patterns))))
          s)))

(def hello?      (respond-to "hi" "yo" "hello" "hey" "morning?" "afternoon"
                             "evening?" "sup" "what'?s up" "wassup" "gutten"
                             "ciao" "hola" "bonjour" "salut"))
(def yes?        (respond-to "yes" "yeah" "yeh" "ya" "yup" "yep" "booyah?"
                             "ok" "okay" "alright" "please" "totally" "sure"
                             "definitely" "absolutely" "affirmative" "yarr?"
                             "yah" "go on" "certainly" "si" "ja" "oui"
                             "(good|great|nice|fantastic) idea"))
(def no?         (respond-to "no" "not" "nah" "nar" "never" "negative" "nein"
                             "non" "changed" "don'?t"))
(def tea?        (respond-to "cuppa" "tea" "brew" "cup" "drink" "beverage"
                             "refreshment"))
(def add-person? (respond-to "[A-Z0-9._%+-]+@[A-Z0-9.-]+[.][A-Z]{2,4}"))
(def tea-prefs?  (respond-to "earl gr[ae]y" "mint" "peppermint" "milk" "sugar"
                             "lemon" "white" "black" "green" "roo?ibos" "chai"
                             "ceylon" "camomile" "lapsang" "souchong" "honey"
                             "english breakfast" "herb(al)? tea"))
(def go-away?    (respond-to "go away" "busy" "from home" "not today" "not in"
                             "wfh" "shut up"))
(def away?       (respond-to "wfh" "away" "out" "home" "not here" "holiday"
                             "(not|don'?t) disturb"))
(def help?       (respond-to "help"))
(def gordon?     (respond-to "^what is \".*\"" "^who knows about \".*\""
                             "^who (can|do) I talk to about \".*\""))
(def who?        (respond-to "who('s|'re)? .+[?]$"))
(def most?       (respond-to "most" "more"))
(def drunk?      (respond-to "dr[ua]nk" "drinks"))
(def made?       (respond-to "made" "makes" "brewed"))
(def available?  (respond-to "on ?line" "available"))
(def luckiest?   (respond-to "luckiest" "(best|highest) .*ratio"))
(def unluckiest? (respond-to "unluckiest" "(worst|lowest) .*ratio"))
(def what?       (respond-to "what('s|'re)? .+[?]$"))
(def stats?      (respond-to "stats?" "statistics?" "info(rmation)?" "data"))
(def rude?       (apply respond-to (split (decode-str (str
                             "ZnVja3xzaGl0fGJvbGxvY2tzfGJpdGNofGJhc3RhcmR8cGVuaXN8"
                             "Y29ja3xoZWxsfHBpc3N8cmV0YXJkfGN1bnR8Y29mZmVlfHN3eXBl"))
                                          #"[|]")))

(def newbie-greeting
  (one-of "Well hello dear, my name is Mrs Doyle Jr. As I am sure you know by the absence of tea recently, old Mrs Doyle had one of her turns had to retire.\nBut I will do my best to take over her duties, so if you ever want tea, just ask me and I'll see what I can do! Of course if you're busy and don't want me bugging you, just say so and I'll back off.\nIf you need any help, just ask."))

(def help
  (one-of "I'm here to help you with all your tea-related needs:
* If you would like some tea, just ask me and I'll see who else is thirsty.
* If you don't want me bothering you, just tell me and I'll leave you alone until you speak to me again.
* I can tell you who's drunk or made the most tea, who are the luckiest or unluckiest people, and what are some of your statistics (make sure you include a question mark at the end).
* To introduce me to a new drinker, just let me know their email address.
* Have a look at my pretty graphs here: http://whitbury:8080/"))

(def alone-status
  (one-of ":( Leaving you alone. So alone..."))

(def like-tea-arg
  (one-of-arg "So you like your tea '%s'?"))

(def ok
  (one-of "Okay!"))

(def add-person
  (one-of "Oh, I'll go introduce myself then!"
          "Another potential tea drinker? Perfect!"))

(def greeting
  (one-of "Well hello dear"
          "Top o' the mornin' to ya"
          "Hello"
          "Hi"
          "Good morning father"
          "Beautiful day outside isn't it?"
          "How do?"
          "I'm feeling fat, and sassy"))

(def want-tea
  (one-of "Will you have a cup of tea?"
          "Will you have a cup of tea father?"
          "We were just about to have a cup of tea, will you join us?"
          "Join us in a cup of tea?"
          "Tea perchance?"
          "Could I interest you in a brew?"
          "Hot beverage?"
          "Take time for tea?"
          "You look thirsty dear, tea?"
          "You know what I fancy, a cuppa. Will you be joining us?"
          "Will you have a cup of tea? I *probably* won't choose you to make it..."
          "Tea for two, two for tea... will you join us?"
          "What would you say to a cup father?"
          "If a man has no tea in him, he is incapable of understanding truth and beauty. Won't you have a cup?"
          "Fancy a cup o' the hot stuff?"))

(def no-backout
  (one-of "I heard you already, tea's coming up"
          "Your fate is secured, back out now and the whole system crumbles. You *will* have tea."
          "You are already in the list of tea drinkers. There's no getting out of it now."
          "Too late for that, you know you want tea really."
          "I can't hear you, I'm on my way to the kitchen"))

(def ah-grand
  (one-of "Ah, grand! I'll wait a couple of minutes and see if anyone else wants one"
          "Champion."
          "You won't regret it!"
          "Wonderful!"
          "I'm so glad!"
          "Oh I _am_ pleased!"
          "Perfect!"
          "Lovely"
          "Absolutely splendiferous"
          "Marvellous!"
          "Oh good, I do like a cup of tea!"
          "Fabulous!"))

(def ah-go-on
  (one-of "Ah, go on! Won't you just have a cup"
          "There's childers in Africa who can't even have tea. Won't you just have a cup"
          "Ah go on go on go on"
          "Go on, go on, go on"
          "It's no bother, really"
          "It would make me so happy if you'd just have a cup"
          "A cup of tea a day keeps the doctor away."
          "Go on, it'll do you a world of good."
          "If a man has no tea in him, he is incapable of understanding truth and beauty."
          "Are you sure, Father? There's cocaine in it!"))

(def good-idea
  (one-of "Fantastic idea, I'll see who else wants one and get back to you in a couple of minutes"
          "Yay, tea!"
          "I was just about to suggest the same thing. I'll see who else wants one"
          "Coming right up... in a couple of minutes"
          "What a delightful idea! I'll let you know who else is in"
          "Oh yes, let's! I'll ask around"
          "Tea you say? What a wonderful thought, I'll see who else agrees"
          "You do have the best ideas, I'll see who else will join us"))

(def on-your-own
  (one-of "You're on your own I'm afraid, nobody else wants one!"
          "What? Well, this is embarrassing... nobody else seems to want tea :("
          "Well, this is practically unheard of, I could convince *nobody* to have a cup"
          "Sad times indeed, tea for one today"
          "There was nobody... nobody at all, I'm so sorry!"
          "Not _one_ other person, can you believe it?"
          ":( I'm sorry, looks like you'll have to make it yourself"))

(def well-volunteered
  (one-of "Well volunteered! The following other people want tea!"
          "Be a love and put the kettle on would you?"
          "Well, somebody has to make the tea, this time it's you"
          "Your time has come... Go make the tea."
          "You know it's a wonderful thing you're about to do..."
          "You know what, I think it's your turn to make the tea now I think about it."
          "Polly put the kettle on, kettle on, kettle on. You are Polly in this game."
          "Why not stretch those weary legs and have a wander over to the kitchen. Say, while you're there...."))

(def other-offered-arg
  (one-of-arg "%s has been kind enough to make the tea, I'd do it myself only I don't have arms"
              "%s has been kind enough to make the tea"
              "%s has been awarded the honour of providing you with tea"
              "%s is going to make tea for you, isn't that nice?"
              "%s will be bringing you some tea, don't forget to say thanks"
              "%s kindly offered to make the tea"
              "%s is about to selflessly put the kettle on"
              "%s is today's lucky tea lady"
              "%s will soon bring you a warm fuzzy feeling in a cup"))

(def huh
  (one-of "I don't understand what you're saying..."
          "If it's not about tea, I'm afraid I'm not really interested..."
          "Pardon?"
          "Beg pardon?"
          "Hm?"
          "Umm....."
          "Pancakes."
          "I fail to see the relevance..."
          "Is there something I can do for you?"
          "Are you sure you're speaking English?"
          "Now really, whatever does that mean?"
          "I'm afraid I'm just not familiar with this new slang you young people use."
          "You always say that!"))

(def rude
  (one-of "Now that's no way to talk to a lady"
          "Wash your mouth out with soap and water!"
          "Well that's not very polite is it?"
          "You won't get any tea talking like that!"
          "Ah, it's a bit much for me, father. 'Feck' this and 'feck' that. 'You big bastard'. Oh, dreadful language! 'You big hairy arse', 'You big fecker'. Fierce stuff! And of course, the f-word, father, the bad f-word, worse than 'feck' - you know the one I mean."
          "'Eff you'. 'Eff your 'effin' wife'. Oh, I don't know why they have to use language like that. 'I'll stick this 'effin' pitchfork up your hole', oh, that was another one, oh, yes!"))

(def how-to-take-it
  (one-of "How do you take your tea?"
          "And how do you take it?"
          "How do you like it?"
          "Milk? Sugar? Lemon? Shaken? Stirred anticlockwise?"))

(def no-tea-today
  (one-of ":( Alright, I won't bother you again. Say hello if you change your mind"
          "Oh... I'm sorry, I'll leave you alone. Let me know if you change your mind though"))

(def just-missed
  (one-of "Sorry dear, too late!"
          "I'm afraid that ship has sailed..."
          "Too slow I'm afraid"
          "Snooze you lose, we already had tea."
          "You'll have to pay closer attention next time, this round's been and gone"))

(def available
  (one-of "Here are the people that might be interested in a drink:"
          "These people might be available at the moment:"
          "Why don't you ask these people if they are thirsty:"))

(def greediest
  (one-of "The greediest drinkers are:"
          "Well I never, these people do drink a lot:"
          "The REAL tea lovers are:"))

(def industrious
  (one-of "The most industrious tea makers are:"
          "Goodness me, these people have made a lot of tea:"
          "The REAL workers in this place are:"))

(def luckiest
  (one-of "I'm not sure these people have been doing their fair share:"
          "I've got my eye on these people:"))

(def unluckiest
  (one-of "These people seem to have been doing more than their fair share:"
          "I'll try to take it easy on these poor people:"))

(def had-today-singular-arg
  (one-of-arg "Check if %s already has a mug, this isn't their first cup today."
              "%s has already had tea today, maybe they have a mug you can re-use."))

(def had-today-plural-arg
  (one-of-arg "Check if %s already have a mug, this isn't their first cup today."
              "%s have already had tea today, maybe they have a mug you can re-use."))

(def gordon
  (one-of "I don't know about that, why don't you ask Gordon?"
          "Maybe you should speak to Gordon about that."
          "I think Gordon might know something about that."
          "I'm not sure, maybe try Gordon?"
          "Why, you _do_ ask the _strangest_ things! Why don't you go and pester Gordon?"
          "That's really not my cup of tea, have you asked Gordon?"))
