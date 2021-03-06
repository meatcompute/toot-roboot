(ns toot-roboot.core
  (:require [clojure.data.csv :refer [read-csv]]
            [clojure.string :refer [join split]]
            [clojure.walk :refer [keywordize-keys]]
            [twitter.oauth :refer :all]
            [twitter.callbacks :refer :all]
            [twitter.callbacks.handlers :refer :all]
            [twitter.api.restful :refer :all]))


(defn load-credentials
  "FIXME: Needs error handling."
  []
  (:fake-creds (read-string (slurp "resources/creds.edn"))))

(defn make-credentials
  [{:keys [consumer-key consumer-secret app-key app-secret]}]
  (make-oauth-creds consumer-key
                    consumer-secret
                    app-key
                    app-secret))

(def my-creds (make-credentials (load-credentials)))

(def config
  {:archive-location "resources/tweets.csv"
   :patterns {:urls #"(https?:\/\/)?([\da-z\.-]+)\.([a-z\.]{2,6})([\/\w \.-]*)*\/?"
              :retweets #"RT "
              :main-tweets #"MT "
              :mentions #"@"}})

;;;;;;;;;;;;;;;;;;
;; Archive loading
;;;;;;;;;;;;;;;;;;
(defn rows->maps
  "Turns CSV rows into maps, using the header row for the keys"
  [rows]
  (for [row (rest rows)]
    (zipmap (first rows) row)))

(defn matches-patterns? [tweet]
  (some #(re-find % tweet)
        (vals (:patterns config))))

(defn load-tweets
  "Converts the tweet archive file into a sequence of tweet strings."
  [file]
  (->> file
       slurp
       read-csv
       rows->maps
       keywordize-keys
       (map :text)
       (remove matches-patterns?)))

(defn mark-and-split
  "Splits a tweet into words and labels the beginning with :start"
  [tweet]
  (cons :start (split tweet #"\s+")))

(defn make-maps-cleaner
  "FIXME: Transducer candidate."
  [tweets]
  (let [splits (map mark-and-split tweets)]))

(defn partition-tweet [tweet]
  (partition 2 1 (remove #(= "" %)
                         (mark-and-split tweet))))

(defn make-maps
  "FIXME: Holy shit you need to refactor this monstrosity."
  [tweets]
  (for [tweet tweets
        m (for [p (partition-tweet tweet)]
            {(first p) [(second p)]})]
    m))

(defn build
  "Takes a sequences of tweets, creates maps of markov chains, and merges them all."
  [tweets]
  (apply merge-with concat (make-maps tweets)))

;; Save tweet data in a variable
;; FIXME Replace this with something implicit as part of the creation
(def markov-tree
  (-> (:archive-location config)
      load-tweets
      build))

;;;;;;;;
;; Tweet
;;;;;;;;
(defn generate-sentence
  "Begins at nodes marked :start and ends with a period."
  [data]
  (loop [ws (data :start)
         acc []]
    (let [w (rand-nth ws)
          nws (data w)
          nacc (concat acc [w])]
      (if (= \. (last w))
        (join " " nacc)
        (recur nws nacc)))))

#_(generate-sentence markov-tree)

;;;;;;;;;;
;; Helpers
;;;;;;;;;;
;; TODO Serialize with options rather than being so specific
(defn serialize-markov-tree-with-whitespace
  "Writes a prettified serialization of the markov tree to `resources/markov-data.edn`"
  []
  (->> config
       :archive-location
       load-tweets
       build
       clojure.pprint/pprint
       with-out-str
       (spit "test/test_files/markov-data.edn")))

#_(defn -main
    "Accepts tweet-frequency in milliseconds to schedule tweeting."
    [tweet-frequency])
