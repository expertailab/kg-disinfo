(ns kg-disinfo.io
  "Read and write graphs to files and supported formats"
  (:require [clojure.java.io :as io]
            [ubergraph.core :as uber]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn strip-ns
  [keyword]
  (let [result (if (qualified-keyword? keyword)
                 (keyword (str/replace (name keyword) "jsonkg/" ""))
                 keyword)]
    (log/debug (str "Stripped " keyword " = " result))
    result))

(defn json->uber-edge
  [json-edge]
  [(:jsonkg/src-node json-edge)
   (:jsonkg/tgt-node json-edge)
   (->>  (:jsonkg/meta json-edge)
        (map (fn [[k v]]
               [(keyword (name k)) v]))
        (into {}))])

(defn jsonKG->edges
  "Converts a `:jsonKG/graph` datastructure into a vector of uber edges"
  [json-map]
  (log/debug (str  "Reading with map with keys " (keys json-map)
               " and " (count (:jsonkg/edges json-map)) " edges"))
  (->> (:jsonkg/edges json-map)
       (map json->uber-edge)
       (into [])))

(defn parse-json-kg
  [json-str]
  (cheshire/parse-string json-str (fn [key] (keyword (str "jsonkg/" key)))))

(defn write-json-kg
  [jsonKG opts]
  (cheshire/generate-string
   jsonKG
   (merge opts {:key-fn
                ;;(fn [key] (str/replace key ":jsonkg/" ""))
                (fn [key] (name key))
                })))

(defn read-json-digraph
  [path-or-json-str]
  (let [json-str (if (.exists (io/as-file path-or-json-str))
                   (slurp path-or-json-str)
                   path-or-json-str)
        json-map (parse-json-kg json-str)
        edges (jsonKG->edges json-map)]
    (log/info (str "Creating digraph from " (count edges) " edges"))
    (log/info (str "Example edge " (first edges)))
    (apply uber/digraph edges))
  )

(defn uber->json-edge
  [uber-g uber-edge]
  #:jsonkg{:src-node (:src uber-edge)
           :tgt-node (:dest uber-edge)
           :meta (uber/attrs uber-g uber-edge)})

(defn uberg->jsonkg
  [uber-g]
  {:jsonkg/edges (->> (uber/edges uber-g)
                      (map (partial uber->json-edge uber-g))
                      (into [])
                     )})

(defn write-as-json-kg
  [uber-g out-path json-gen-opts]
  (spit out-path
        (-> uber-g
            (uberg->jsonkg)
            (write-json-kg (or json-gen-opts {})))
        ))


;;;
;;;  injections (maps from node ids to initial values to propagate
;;;

(defn parse-json-injection
  [json-str]
  (cheshire/parse-string json-str))

(defn read-injection-json
  [path-or-json-str]
  (let [json-str (if (.exists (io/as-file path-or-json-str))
                   (do
                     (log/info (str "Reading injections from: " path-or-json-str))
                     (slurp path-or-json-str))
                   path-or-json-str)
        inj-map (parse-json-injection json-str)
        json-map (get inj-map "injection")
        ]
    (log/info (str "Read injection with " (count json-map) " injections"))
    json-map))

(defn write-json
  [value opts]
  (cheshire/generate-string
   value
   (merge opts {:key-fn (fn [key] (name key))
                })))

(defn write-injection-json
  [injection-map out-path json-gen-opts]
  (spit out-path
        (write-json {:injection injection-map} (or json-gen-opts {})))
  )

;;;
;;;
;;;
(defn write-propagated-scores-json
  [scores out-path json-gen-opts]
  (spit out-path
        (write-json {:scores scores} (or json-gen-opts {}))))
