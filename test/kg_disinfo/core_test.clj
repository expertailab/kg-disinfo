(ns kg-disinfo.core-test
  (:require [clojure.test :refer :all]
            [kg-disinfo.core :refer :all]
            [kg-disinfo.test-graphs :as test-graphs]
            [ubergraph.core :as uber]))

(deftest a-test
  (testing "FIXME, I fail to generate graph?"
    (let [g-spec {:rel-counts {:isOwner 2 :isAuthor 2}}
          g (test-graphs/generate-dante-test-graph g-spec)]
      (uber/pprint g)
      (is (= 0 1)))
    ))
