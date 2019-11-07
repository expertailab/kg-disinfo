(ns kg-disinfo.generator
  "Provides methods for generating a random graph based on a specification
  ```clj
    (generator/generate-test-graph
     {
       :nodes {:person 3 :article 6 :publisher 2}
       :rel-counts {:authorOf 2 :publishedOn 3 :follows 4}
       :inverse-rel-map {:authorOf :byAuthor
                         :publishedOn :published}
       :default-reltype-weights
       {:authorOf {:source :article :target :person :weight 1.0 :comment \"credible articles should give the author credibility\"}
        :byAuthor {:source :person :target article :weight 1.0 :comment \"credible authors write credible articles\")
        :publishedOn {:source :article :target :publisher :weight 1.0}
        :published {:source :publisher :target :article :weight 0.5}
        :follows {:source person :target person :weight 0.1 :comment \"credible people don't necessarily follow credible people\"}}})
  ```"
  (:require [ubergraph.core :as uber]
            ))

;;;
;;; Node generation
;;;

(defn generate-symbols
  "Generates `n` unique symbols starting with the specified prefix"
  [n prefix]
  (map (fn [ignore] (str (gensym prefix))) (range n)))

(defn generate-nodes
  [gen-spec]
  (let [nodespec (:nodes gen-spec)
        gen-symbs (fn [node-type]
                    (generate-symbols (node-type nodespec 3)
                                   (str (name node-type) "_")))]
    (->> (keys nodespec)
         (map (fn [node-type]
                [node-type (gen-symbs node-type)]))
         (into {}))))


;;;
;;; Edge generation
;;;

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
  [{:keys [source target]} nodes-by-type]
  (let [source-candidates (get-nodes source nodes-by-type)
        target-candidates (get-nodes target nodes-by-type)]
    (if (nil? source-candidates)
      (print "nodes-by-type does not contain " source "?"
             (keys nodes-by-type) "?\n"))
    (if (nil? target-candidates)
      (print "nodes-by-type does not contain " target "?"
             (keys nodes-by-type) "?\n"))
    [(rand-nth source-candidates)
     (rand-nth target-candidates)]
  ))

(defn generate-edge
  [cfg rel-type nodes-by-type]
  (let [rel-meta (get-in cfg [:default-reltype-weights rel-type])
        pair (select-node-pair rel-meta nodes-by-type)
        ]
    ;;(print "edge for random pair" pair "\n")
    (conj pair {:weight (:weight rel-meta) :name (name rel-type)})
    ))

(defn generate-n-edges
  [cfg [rel-type count] nodes-by-type]
  (map (fn [ignore] (generate-edge cfg rel-type nodes-by-type)) (range count)))

(defn generate-edges
  "Generates relations based on a generation spec and a map of nodes by type"
  [gen-spec nodes-by-type]
  (mapcat (fn [rel-type-count] (generate-n-edges gen-spec rel-type-count nodes-by-type))
          (seq (:rel-counts gen-spec))))


;;;
;;; Inverse edge generation
;;;

(defn invertible-edge?
  [[src-node tgt-node attrs] cfg]
  (contains? (into #{} (keys (:inverse-rel-map cfg))) (keyword (:name attrs))))

(defn generate-inverse
  [edges cfg]
  (->> edges
       (filter (fn [edge] (invertible-edge? edge cfg)))
       (map (fn [[src-node tgt-node attrs]]
              (let [rel-type (keyword (:name attrs))
                    inv-type (get-in cfg [:inverse-rel-map rel-type]) 
                    inv-weight (get-in cfg
                                       [:default-reltype-weights inv-type :weight]) 
                    ]
                [tgt-node src-node {:weight inv-weight :name (name inv-type)}]))
            )))

(defn add-inverse-edges
  [edges cfg]
  (into edges (generate-inverse edges cfg)))


;;;
;;;  Main graph generation function 
;;;

(defn generate-test-graph
  "Generates a digraph based on a generation spec
  The `gen-spec` must be a map with keys `:nodes`, `:rel-counts`,
  `:inverse-rel-map` and `:default-reltype-weights`"
  [gen-spec]
  (let [V (generate-nodes gen-spec)
        E (generate-edges gen-spec V)
        cfg gen-spec]
    (apply uber/digraph (add-inverse-edges E cfg))))
