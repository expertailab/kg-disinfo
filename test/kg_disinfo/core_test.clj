(ns kg-disinfo.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as spec]
            [danteg]
            [kg-disinfo.core :refer :all]
            [kg-disinfo.appleseed :as appleseed]
            [kg-disinfo.dante-graphs :as dante-graphs]
            [ubergraph.core :as uber]))

(def readable-cfg
  ;; config useful for having small node objects (easier to inspect graph results)
  {:ent-map-fn (fn [ent] (-> ent :danteg/convertibleToCard :danteg/uuid))
            :rel-map-fn (fn [rel] (-> rel :danteg/relation :danteg/relationUuid))}
  )

(comment 
  (deftest test-parse-json-dante-graph
    (let [json-str (slurp "resources/graph_for_disinformation_1.txt")
          dante-g (dante-graphs/parse-json-dante-graph json-str)]
      (when (not (spec/valid? :danteg/dante-graph dante-g))
        (do 
                                        ;(spec/explain :danteg/dante-graph dante-g)
          (is false "does not conform to spec"))
        ))))

(deftest test-write-json-dante-graph
  (let [dante-g {:danteg/id 5 :danteg/uuid "asdf"}]
    (is (= (dante-graphs/write-json-dante-graph dante-g)
           "{\"id\":5,\"uuid\":\"asdf\"}")))
  )


(deftest test-read-seed-injections
  (let [json-str (slurp "resources/graph_for_disinformation_1.txt")
        dante-g (dante-graphs/parse-json-dante-graph json-str)
        ;g (dante-graphs/create-from-dante-graph dante-g readable-cfg)
        seed-injections (dante-graphs/read-seed-injections dante-g readable-cfg)]
    (println "seed injections " seed-injections)
    (is (not (empty? seed-injections)))
    ))

(defn =roundf
  [dec-points a b]
  (assert (int? dec-points))
  (assert (> dec-points 0))
  (let [format-exp (str "%." dec-points "f")]
    (= (format format-exp a) (format format-exp b))))

(deftest test-appleseed-on-dante-test-graph_1
  (let [json-str (slurp "resources/graph_for_disinformation_1.txt")
        dante-g (dante-graphs/parse-json-dante-graph json-str)
        g (dante-graphs/create-from-dante-graph dante-g readable-cfg)
        seed-injections (dante-graphs/read-seed-injections dante-g readable-cfg)
        spreading-factor 0.9
        acc-threshold 0.001]
    (println "dante-g entities type " (type (:danteg/uiEntitiesList dante-g)))
    (let [prop-scores (time (appleseed/appleseed* g seed-injections spreading-factor acc-threshold))]
      (is (not (empty? prop-scores)))
      (println "prop scores for test graph 1: " prop-scores)
      (is (=roundf 5 0.725538 (get prop-scores "KB-KB_API-Person_08455784-9011-4dbf-b506-1df98cfa2b02")))
      (is (=roundf 5 0.178389 (get prop-scores "KB-KB_API-Event_e186935a-d199-4654-b80d-130d9eb8df7e")))
      (is (=roundf 5 0.334531 (get prop-scores "KB-KB_API-Person_253ed624-1d20-44ee-a1a2-1c160561da9f")))
      (is (=roundf 5 0.405504 (get prop-scores "KB-KB_API-Organization_148cca61-d841-457e-b73f-c93f0f7d2405")))
      (is (=roundf 5 0.334531 (get prop-scores "KB-KB_API-Person_9527738b-d61b-4af3-be03-116cfe5cb387")))
      )
    ))

(deftest test-appleseed-on-dante-test-graph_2
  (let [json-str (slurp "resources/graph_for_disinformation_2.txt")
        dante-g (dante-graphs/parse-json-dante-graph json-str)
        g (dante-graphs/create-from-dante-graph dante-g readable-cfg)
        seed-injections (dante-graphs/read-seed-injections dante-g readable-cfg)
        spreading-factor 0.9
        acc-threshold 0.001]
    (let [prop-scores (time (appleseed/appleseed* g seed-injections spreading-factor acc-threshold))]
      (is (not (empty? prop-scores)))
      (is (= (count seed-injections) (count prop-scores)))
      )
    )
  )

(deftest test-read-danteg
  (let [g (dante-graphs/create-from-dante-graph
           "resources/graph_for_disinformation_1.txt" readable-cfg
           )]
    (uber/pprint g)
    (is (not (uber/undirected-graph? g)))
    (is (= 5 (count (uber/nodes g))))
    (is (= 4 (count (uber/edges g))))
  ))

(deftest test-process-dante-graph-json_1
  (let [json-str (slurp "resources/graph_for_disinformation_1.txt")
        out-json (process-dante-graph-json json-str)]
    (spit "target/in-graph-1.json" json-str)
    (spit "target/out-graph-1.json" out-json)
    (is (not (= json-str out-json)))
    ))

(comment 
  (deftest a-test
    (testing "FIXME, I fail to generate graph?"
      (let [g-spec {:rel-counts {:isOwner 2 :isAuthor 2}}
            g (dante-graphs/generate-dante-test-graph g-spec)]
        (uber/pprint g)
        (is (= 0 1)))
      )))
