(ns serf.core-test
  (:require [clojure.test :refer :all]
            [jepsen.core :as jepsen]
            [serf.core :as serf]))
(deftest serf-test
  (is (:valid? (:results (jepsen/run! (serf/serf-test 60))))))
(deftest serf-test-rand-halves
  (is (:valid? (:results (jepsen/run! (serf/serf-test-rand-halves 600))))))
(deftest serf-test-bridge
  (is (:valid? (:results (jepsen/run! (serf/serf-test-bridge 600))))))

