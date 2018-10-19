(defproject kg-disinfo "0.1.1-SNAPSHOT"
  :description "Disinformation estimation through spreading activation"
  :url "http://172.16.175.101/rdenaux/kg-disinfo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ubergraph "0.5.1"]]
  :plugins [[refactor-nrepl "2.4.0"]
           [cider/cider-nrepl "0.17.0"]]
  :main ^:skip-aot kg-disinfo.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
