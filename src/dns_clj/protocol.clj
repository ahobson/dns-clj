(ns dns-clj.protocol
  (:require [clojure.string :as str]
            [gloss (core :refer [defcodec] :as glc) (io :as gio)]))

(defcodec dns-header
  (glc/ordered-map :id :uint16-be
                   :options (glc/bit-map :qr 1
                                         :opcode 4
                                         :aa 1
                                         :tc 1
                                         :rd 1
                                         :ra 1
                                         :z 3
                                         :rcode 4)
                   :qdcount :uint16-be
                   :ancount :uint16-be
                   :nscount :uint16-be
                   :arcount :uint16-be))

(defcodec label
  (glc/finite-frame :byte (glc/string :ascii)))

(defn string->label-array
  "Convert the string `s` representing a dns qname into an array of
  labels."
  [s]
  (when s
    (str/split s #"\.")))

(defn label-array->string
  "Convert the array of labels `a` into a string represending a dns
  qname."
  [a]
  (str/join "." a))

(defcodec qname
  (glc/compile-frame
   (glc/repeated label :delimiters [(byte 0)])
   string->label-array
   label-array->string))

(defcodec qtypes
  ;; only support a handful of qtypes for now.
  (glc/enum :uint16-be {:a 1
                        :cname 5
                        :any 255}))

(defcodec class-types
  ;; the latter three are never used, but might as well define them.
  (glc/enum :uint16-be {:in 1
                        :cs 2
                        :ch 3
                        :hs 4}))

(defcodec question
  (glc/ordered-map :qname qname
                   :qtype qtypes
                   :qclass class-types))

(defcodec rdata-a
  (glc/finite-frame :uint16-be (glc/ordered-map :address :uint32-be)))

(defcodec rdata-c
  (glc/finite-frame :uint16-be (glc/ordered-map :cname qname)))

(def answer-codecs
  {:a     rdata-a
   :cname rdata-c})

(defcodec answer-class-ttl
  (glc/ordered-map :name qname
                   :type qtypes
                   :class class-types
                   :ttl :uint32-be))

(defn build-merge-header-with-data
  "Build a function that takes a header and returns a compiled
  frame (using `frame-fn`) that post-processes the frame to merge the
  header and the data."
  [frame-fn]
  (fn [h]
    (glc/compile-frame
     (frame-fn h)
     identity
     (fn [data]
       (merge h data)))))

(defcodec rrec
  (glc/header answer-class-ttl
              (build-merge-header-with-data
               (fn [h] (get answer-codecs (:type h))))
              (fn [b]
                (select-keys b [:name :type :class :ttl]))))

(defn dns-msg-body-codec
  "Create a codec representing the body of the dns message from header
  `h`."
  [h]
  (glc/ordered-map :question (repeat (:qdcount h) question)
                   :answer   (repeat (:ancount h) rrec)
                   :authority (repeat (:nscount h) rrec)
                   :additional (repeat (:arcount h) rrec)))

(defcodec dns-msg
  (glc/header dns-header
              (build-merge-header-with-data
               dns-msg-body-codec)
              (fn [b]
                {:id (:id b)
                 :options (:options b)
                 :qdcount (count (:question b))
                 :ancount (count (:answer b))
                 :nscount (count (:authority b))
                 :arcount (count (:additional b))})))
