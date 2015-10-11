(defproject dns-clj "0.1.0-SNAPSHOT"
  :description "Sample gloss/aleph app"
  :url "https://github.com/ahobson/dns-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [gloss "0.2.5"]
                 [aleph "0.4.0"]]
  :main ^:skip-aot dns-clj.core
  :profiles {:uberjar {:aot :all}})
