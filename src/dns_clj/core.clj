(ns dns-clj.core
  (:require [aleph.udp :as udp]
            [gloss (io :as gio)]
            [manifold.stream :as ms]
            [dns-clj.protocol :as dnsp]))

(def ^:dynamic *default-port* 53)

(def rcodes {:ok 0
             :format 1
             :servfail 2
             :nxdomain 3
             :notimpl 4
             :refused 5})

(def answer-db
  {"foo.example" [{:type :a     :class :in :ttl 300
                   :name "foo.example" :address 0x0a020304}]
   "bar.example" [{:type :cname :class :in :ttl 300
                   :name "bar.example" :cname "foo.example"}]})

(defn build-response
  "Build a response structure from `query`. This is a toy lookup
  function since it uses the hard coded data in `answer-db`."
  [query]
  (let [qname (get-in query [:question 0 :qname])
        qtype (get-in query [:question 0 :qtype])
        all-answers (get answer-db qname [])
        answers (filter #(or (= :any qtype) (= (:type %) qtype)) all-answers)
        rcode (if-not (empty? answers) (:ok rcodes) (:nxdomain rcodes))]
    {:id (:id query)
     :options {:qr true
               :opcode (get-in query [:options :opcode])
               :aa true
               :tc false
               :rd (get-in query [:options :rd])
               :ra false
               :z 0
               :rcode rcode}
     :question (:question query)
     :answer answers
     :authority []
     :additional []}))

(defn answerer
  "Answer `msg` on manifold stream `s`."
  [s msg]
  (let [d (gio/decode dnsp/dns-msg (:message msg))
        resp (build-response d)]
    (ms/put! @s (assoc msg :message (gio/encode dnsp/dns-msg resp)))))

(defn start-server
  "Start the DNS server on `port`"
  [port]
  (let [s (udp/socket {:port port :broadcast? false})]
    (ms/consume (partial answerer s) @s)
    s))

(defn -main
  [& args]
  (let [port-str (first args)
        port (if port-str (Integer/parseInt port-str) *default-port*)]
    (start-server port)))
