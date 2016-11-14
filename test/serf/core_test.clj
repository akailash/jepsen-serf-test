(ns serf.core-test
  (:require [clojure.test :refer :all]
            [jepsen.core :as jepsen]
            [serf.core :as serf]))
(deftest serf-test
  (is (:valid? (:results (jepsen/run! (serf/serf-test "0.8.0"))))))

