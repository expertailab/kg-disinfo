(require 'cemerick.pomegranate.aether)
(cemerick.pomegranate.aether/register-wagon-factory!
  "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))
(defproject net.expertsystem.lab/kg-disinfo "0.2.0"
  :description "Disinformation estimation through spreading activation"
  :url "http://172.16.175.101/rdenaux/kg-disinfo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [org.clojure/tools.cli "0.4.2"]
                 [cheshire "5.8.1"]
                 [ubergraph "0.5.1"]]
  :plugins [[refactor-nrepl "2.4.0"]
           [cider/cider-nrepl "0.17.0"]]
  :deploy-repositories [["esi-private" {:url "http://nexus.iberia.expertsystem.local/repository/esi-private"
                                        :sign-releases false}]]
  :jvm-opts ["-Dfile.encoding=UTF-8"]
  :main ^:skip-aot kg-disinfo.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
