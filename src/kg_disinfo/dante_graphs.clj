(ns kg-disinfo.dante-graphs
  (:require  [ubergraph.core :as uber]))

;; provides methods for generating random dante graphs which can be used as inputs for
;; the appleseed algorithm. Once we have access to the Dante KG we can implement methods for
;; converting (parts of) that graph into uber graphs suitable for appleseed

(def inverse-rel-map
  {:isRelatedTo :isRelatedTo-1
   :isAuthor :isAuthor-1
   :isOwner :isOwner-1
   :isMember :isMember-1})

(def default-reltype-weights
  {:isRelatedTo {:source :person :target :event :weight 1.0 :comment "If a resource is deemed disinforming, all events extracted from said resource should also be considered disinforming"}
   :isRelatedTo-1 {:source :event :target :person :weight 1.0 :comment "If an event is deemed to be disinformation, all resources reporting that event should also be considered disinformative"}
   :involvesPerson {:source :event :target :person :weight 0.1 :comment "If an event is deemed disinforming, we should not necessarily distrust a person, digital identity or group that was involved in said event."}
   :involvesDigitalIdentity {:source :event :target :digitalId :weight 0.1 :comment "If an event is deemed disinforming, we should not necessarily distrust a person, digital identity or group that was involved in said event."}
   :involvesGroup {:source :event :target :group :weight 0.1 :comment "If an event is deemed disinforming, we should not necessarily distrust a person, digital identity or group that was involved in said event."}
   :involvesObject {:source :event :target :object :weight 0.0 :comment "We do not want to assign disinformation scores to Object instances."}
   :isAuthor {:source #{:digitalId :person} :target :resource :weight 0.9 :comment "If a person or digital identity is deemed untrustworthy, we can expect resources produced by said person to contain disinformation."}
   :isAuthor-1 {:source :resource :target #{:digitalId :person} :weight 1.0 :comment "If a Resource is deemed disinformative, the author(s) should also be deemed untrustworthy."}
   :isOwner {:source :person :target :digitalId :weight 1.0 :comment "If a Person is deemed untrustworthy, their digital accounts should also be deemed untrustworthy."}
   :isOwner-1 {:source :digitalId :target :person :weight 1.0 :comment "If a Digital identity is deemed untrustworthy, the person(s) behind that account should also be deemed untrustworthy."}
   :isMember {:source #{:person :group} :target :organization :weight 1.0 :comment "If a Person or Group is deemed untrustworthy and they are members of an organization, the organization should also be considered untrustworthy and a likely spreader of disinformation."}
   :isMember-1 {:source :organization :target #{:person :group} :weight 1.0 :comment "If an organization is deemed untrustworthy and a spreader of disinformation, its members should also be deemed untrustworthy and likely to spread disinformation."}
    })

(defn generate-symbols
  [n prefix]
  (map (fn [ignore] (keyword (gensym prefix))) (range n)))

(defn generate-nodes
  [gen-spec]
  ;;todo: generate nodes based on spec
  (let [nodespec (:nodes gen-spec)]
    {:person (generate-symbols (:person nodespec 4) "p_")
     :event (generate-symbols (:event nodespec 2) "e_")
     :resource (generate-symbols (:resource nodespec 4) "r_")
     :digitalId (generate-symbols (:digitalId nodespec 2) "d_")
     :group (generate-symbols (:group nodespec 2) "g_")
     :object (generate-symbols (:object nodespec 3) "ob_")
     :organization (generate-symbols (:organization nodespec 2) "org_")})
  )

(defn get-nodes
  [source nodes-by-type]
  (cond
    (keyword? source) (source nodes-by-type)
    (set? source) (get-nodes (rand-nth (into [] source)) nodes-by-type)
    :else (let [msg (str "Unknown source type " source "?\n")]
           (print msg)
           [])
    ))

(defn select-node-pair
  [rel-type nodes-by-type]
  (let [rel-meta (rel-type default-reltype-weights)
        source-candidates (get-nodes (:source rel-meta) nodes-by-type)
        target-candidates (get-nodes (:target rel-meta) nodes-by-type)]
    (if (nil? source-candidates) (print "nodes-by-type does not contain " (:source rel-meta) "?" (keys nodes-by-type) "?\n"))
    (if (nil? target-candidates) (print "nodes-by-type does not contain " (:source rel-meta) "?" (keys nodes-by-type) "?\n"))
    [(rand-nth source-candidates)
     (rand-nth target-candidates)]
  ))

(defn generate-edge
  [rel-type nodes-by-type]
  (let [pair (select-node-pair rel-type nodes-by-type)
        rel-meta (rel-type default-reltype-weights)]
    ;;(print "edge for random pair" pair "\n")
    (conj pair {:weight (:weight rel-meta) :name rel-type})
    ))

(defn generate-n-edges
  [rel-type-count nodes-by-type]
  (let [rel-type (nth rel-type-count 0)
        count (nth rel-type-count 1)]
    ;;(print "generating" count rel-type "\n")
    (map (fn [ignore] (generate-edge rel-type nodes-by-type)) (range count))
    ))

(defn generate-edges
  [gen-spec nodes-by-type]
  (mapcat (fn [rel-type-count] (generate-n-edges rel-type-count nodes-by-type))
          (seq (:rel-counts gen-spec)))
  )

(defn invertible-edge?
  [edge]
  (let [attrs (nth edge 2)]
    (contains? (into #{} (keys inverse-rel-map)) (:name attrs))
    )
  )

(defn generate-inverse
  [edges]
  (map (fn [edge]
         (let [src-node (nth edge 0)
               tgt-node (nth edge 1)
               attrs (nth edge 2)
               in-type (:name attrs)
               inv-type (in-type inverse-rel-map)
               rel-meta (inv-type default-reltype-weights)]
           [tgt-node src-node {:weight (:weight rel-meta) :name inv-type}]))
       (filter invertible-edge? edges))
  )

(defn add-inverse-edges
  [edges]
  (into edges (generate-inverse edges))
  )

(defn normalise-scores
  [m]
  (let [max-score (+ 0.000001 (apply max (vals m)))]
    (zipmap (keys m)
            (map 
             (fn [k]
               (/ (m k) max-score))
             (keys m))))
  )

(defn hexify [val] (format "%02X" val))

(defn calc-hex-color
  [normalised-score]
  (let [value (hexify (- 255 (int (* 255 normalised-score))))]
    (str "#ff" value value))
  )

(defn color-by-scores
  [g trust-scores]
  (let [norm-scores (normalise-scores trust-scores)]
    (reduce (fn [g n]
              ;;(print "calc-ed color for " n " " (calc-hex-color (n norm-scores 0.0)) "\n")
              (uber/add-attrs g n
                              {:style :filled
                               :fillcolor (calc-hex-color (n norm-scores 0.0))} ))
            g (uber/nodes g)))
  )

(defn choose-from
  [candidates percentage]
  (let [total (count candidates)
        n (max 1 (int (* percentage total)))]
    (repeatedly n #(rand-nth candidates)))
  )

(defn generate-injection
  [g node-filter]
  (let [filtered-nodes (filter node-filter (uber/nodes g))
        seeds (choose-from filtered-nodes 0.15)]
        (zipmap seeds
                (repeat (count seeds) 1.0)))
  )

(def dante-g-spec-small
  {:nodes {:person 6 :group 3 :event 6 :resource 10 :digitalId 3 :object 3 :organization 1}
   :rel-counts {:isOwner 3 :isAuthor 6 :isRelatedTo 10
                :isMember 4 :involvesPerson 4
                :involvesDigitalIdentity 2
                :involvesGroup 3
                :involvesObject 3}})

(def dante-g-spec-med
  {:nodes {:person 60 :group 30 :event 60 :resource 100 :digitalId 30 :object 30 :organization 10}
   :rel-counts {:isOwner 30 :isAuthor 60 :isRelatedTo 100
                :isMember 40 :involvesPerson 40
                :involvesDigitalIdentity 20
                :involvesGroup 30
                :involvesObject 30}})

(def dante-g-spec-lar
  {:nodes {:person 600 :group 300 :event 600 :resource 1000 :digitalId 300 :object 300 :organization 100}
   :rel-counts {:isOwner 300 :isAuthor 600 :isRelatedTo 1000
                :isMember 400 :involvesPerson 400
                :involvesDigitalIdentity 200
                :involvesGroup 300
                :involvesObject 300}})

(defn generate-dante-test-graph
  [gen-spec]
  (let [V (generate-nodes gen-spec)
        E (generate-edges gen-spec V)]
    (apply uber/digraph (add-inverse-edges E))))
