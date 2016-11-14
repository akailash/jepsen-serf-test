(ns serf.response)

(defn raise-on-error
  "Throw a generic exception if an error is returned as part of the response."
  [responses]
  (doseq [response responses]
    (when (and (response "Error") (not= "" (response "Error")))
      (throw (Throwable. (response "Error"))))))

(defmulti parse
  "Return a more legit Clojure data structure from a list of Serf responses."
  (fn [command responses] command))

(defmethod parse :members [command responses]
  (let [members ((second responses) "Members")]
    members))

(defmethod parse :query [command responses]
  (letfn [(parse-payload [payload]
            (apply str (map char payload)))
          (fold-responses [acc response]
            (if (= (response "Type") "response")
              (assoc acc (response "From") (parse-payload (response "Payload")))
              acc))]
    (reduce fold-responses {} responses)))

(defmethod parse :default [command responses] responses)
