
(ns serf.core
  (:require [clojure.tools.logging :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [jepsen [db    :as db]
                    [client  :as client]
                    [generator :as gen]
                    [util :refer [timeout]]
                    [control :as c]
                    [nemesis    :as nemesis]
                    [tests :as tests]]
            [jepsen.os.debian :as debian]
            [serf.client :refer [make-client send-command!]]))

(defn db
  "Serf service"
  []
  (reify db/DB
    (setup! [_ test node]
      (info node "Starting Serf")
      (c/exec :service :serf :start))
    (teardown! [_ test node]
      (info node "Tearing down Serf")
      (c/exec :service :serf :stop)
      (c/exec :rm :-rf
       (c/lit "/var/log/serf/*")))
    db/LogFiles
    (log-files [_ test node]
      ["/var/log/serf/serf.log"
       "/var/log/serf/random.txt"
       "/var/log/serf/purgelist.txt"])))

(def printable-ascii (->> (concat (range 48 68)
                                  (range 66 92)
                                  (range 97 123))
                          (map char)
                          char-array))

(defn rand-str
  "Random ascii string of n characters"
  [n]
  (let [s (StringBuilder. n)]
    (dotimes [i n]
      (.append s ^char
               (->> printable-ascii
                    alength
                    rand-int
                    (aget printable-ascii))))
                    (.toString s)))


(defn s [_ _] {:type :invoke, :f :send, :value "purge"})


(defn client
  "A client for a single compare-and-set register"
  [c]
  (reify client/Client
    (setup! [_ test node]
      (info node "Starting client")
      (let [c (make-client {:host (name node) :port 7373})]
      (send-command! c :handshake)
      (client c)))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info, :error :timeout)
               (case (:f op)
                 :send (do (send-command! c :event (:value op) (rand-str (rand-int 100)) false)
                           (assoc op :type :ok)))))

    (teardown! [_ test]
      (.close c))))


(defn serf-test
 "Basic test with 10events per sec and no nemesis"
  [duration]
  (assoc tests/noop-test
         :name "Serf"
         :os debian/os
         :db (db)
         :client (client nil)
         :generator (->> s
                         (gen/stagger 1/10)
                         (gen/clients)
                         (gen/time-limit duration))))

(defn serf-test-rand-halves
 "Test with 10events per sec and nemesis partition-random-halves"
  [duration]
  (assoc tests/noop-test
         :name "Serf"
         :os debian/os
         :db (db)
         :client (client nil)
         :nemesis (nemesis/partition-random-halves)
         :generator (->> s
                         (gen/stagger 1/10)
                         (gen/nemesis
                           (gen/seq (cycle [(gen/sleep 10)
                                            {:type :info, :f :start}
                                            (gen/sleep 120)
                                            {:type :info, :f :stop}])))
                         (gen/time-limit duration))))


(defn serf-test-bridge
 "Test with 10events per sec and nemesis bridge and shuffle"
  [duration]
  (assoc tests/noop-test
         :name "Serf"
         :os debian/os
         :db (db)
         :client (client nil)
         :nemesis (nemesis/partitioner (comp nemesis/bridge shuffle))
         :generator (->> s
                         (gen/stagger 1/10)
                         (gen/nemesis
                           (gen/seq (cycle [(gen/sleep 10)
                                            {:type :info, :f :start}
                                            (gen/sleep 120)
                                            {:type :info, :f :stop}])))
                         (gen/time-limit duration))))
