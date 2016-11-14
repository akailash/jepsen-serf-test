(ns serf.client
  (:require [clojure.core.incubator :refer [dissoc-in]]
            [msgpack.core :refer [pack unpack]]
            [serf.command :refer [send-command]]
            [serf.response :refer [parse raise-on-error]])
  (:import [java.net Socket SocketTimeoutException]
           [java.io BufferedReader DataOutputStream]))

(declare receive)

(def responses (ref {}))

(defn make-client
  "Create an TCP socket for communicating with the Serf agent."
  ([] (make-client {}))
  ([{host :host port :port}]
     (let [socket (new Socket (or host "localhost") (or port 7373))]
       (.setSoTimeout socket 2000)
       {:socket socket
        :in (.getInputStream socket)
        :out (.getOutputStream socket)})))

(defn- wait-for-done
  "Wait until all responses from this seq have been received."
  [client command request-seq]
  (receive client)
  (when (#{:query} command)
    (let [seq-responses (get-in @responses [client request-seq])]
      (when-not (seq (filter #(= "done" (% "Type")) seq-responses))
        (recur client command request-seq)))))

(defn send-command!
  "Synchronously send a request, wait for the response, and return it."
  [client command & args]
  (let [request-seq (apply send-command client command args)]
    (wait-for-done client command request-seq)
    (dosync
     (let [response (get-in @responses [client request-seq])]
       (commute responses dissoc-in [client request-seq])
       (raise-on-error response)
       (parse command response)))))

(defn- unpack-multiple
  "Unpack multiple msgpack structures from a byte sequence,
  returning them as a seq. Fairly inefficient."
  [byte-seq]
  (loop [stream byte-seq
         acc []]
    (if (seq stream)
      (let [obj (unpack stream)
            byte-count (count (pack obj))]
        (recur (drop byte-count stream) (conj acc obj)))
      acc)))

(defn- read-socket [client]
  (try
    (let [first (.read (:in client))]
      (loop [acc [first]]
        (if-not (zero? (.available (:in client)))
          (recur (conj acc (.read (:in client))))
          (seq acc))))
    (catch SocketTimeoutException e nil)))

(defn- receive
  "Receive potentially multiple commands from the client connection
  and add them to the responses ref."
  [client]
  (let [received (read-socket client)
        response-objs (unpack-multiple received)]
    (loop [response-objs response-objs]
      (let [this-response (first response-objs)
            next-response (second response-objs)]
        (if-not (nil? this-response)
          (if (or (nil? next-response) (next-response "Seq"))
            (do (dosync
                 (commute responses
                          assoc-in
                          [client (this-response "Seq")]
                          [this-response]))
                (recur (rest response-objs)))
            (do (dosync
                 (commute responses
                          assoc-in
                          [client (this-response "Seq")]
                          (conj (or (get-in @responses [client (this-response "Seq")]) []) this-response next-response)))
(recur (drop 2 response-objs)))))))))
