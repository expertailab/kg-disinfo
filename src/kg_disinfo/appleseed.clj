(ns kg-disinfo.appleseed
  "Implements the appleseed algorithm"
  (:require [ubergraph.core :as uber]
            ))

(defn new-nodes-in
  "Returns a set of new target-nodes that are reachable from source-nodes"
  [graph source-nodes]
  (let [tgt-nodes (mapcat
                   (fn [src-node] ;; get-target-nodes from src-node
                     (map :dest (uber/find-edges graph {:src src-node})))
                   source-nodes)]
    (into #{} (filter (fn [node] (not (contains? source-nodes node))) tgt-nodes))
    ))

(defn trust-change
  [spreading-factor injection-map]
  (zipmap (keys injection-map)  
          (map (fn [k] (* (- 1 spreading-factor)
                         (get injection-map k)))
               (keys injection-map))))

(defn combine-maps
  [map-a map-b val-merge-fn defval-a defval-b]
  (let [keys-ab (into (into #{} (keys map-a)) (keys map-b))]
    (zipmap keys-ab
            (map (fn [k] (apply val-merge-fn [(get map-a k defval-a) (get map-b k defval-b)]))
                 keys-ab)
            )))


(defn calc-in-contribs
  "Returns a LazySeq with the weighted contributions for each in-edge"
  [in-edges in_prev graph]
  (map (fn [in-edge]
         (let [src-node (:src in-edge)
               edge-weight (uber/attr graph in-edge :weight)
               out-edges (uber/find-edges graph {:src src-node})
               out-weights (map (fn [e] (uber/attr graph e :weight)) out-edges)]
           (* (get in_prev src-node 0.0) ;; use 0.0 if no previous value known
              (/ edge-weight
                 ;;avoid division by 0
                 (+ 0.0001 (apply + out-weights)))))
         )
       in-edges)
  )

(defn update-incoming
  "Returns an updated version of in_prev
  for the nodes already in in_prev and the new nodes"
  [in_prev graph spreading-factor V_new]
  (let [nodes (into (keys in_prev) V_new)]
    (zipmap nodes
            (map (fn [k]
                   (let [in-edges (uber/find-edges graph {:dest k})
                         in-contribs (calc-in-contribs in-edges in_prev graph)]
                     ;;(print "weighted input contributions for node " k ": " in-contribs "\n")
                     (* spreading-factor
                        (apply + in-contribs))
                     ))
                 nodes)))
  )

(defn add-virtual-nodes
  [graph seed-node new_nodes]
  (uber/add-directed-edges*
   graph
   (map (fn [node]
          [node seed-node {:weight 1.0 :name :appleseed-virtual}])
        new_nodes))
  )

(defn appleseed
  "Implements the appleseed trust metric.
  Returns a map from nodes in the graph to updated scores.
  see also https://pdfs.semanticscholar.org/2635/19ea851c96e67b550bf03b4ec8922beeb120.pdf
  "
  [graph seed-node trust-injection spreading-factor accuracy-threshold]
  (loop [i 0
         g_i graph
         V_i #{seed-node}
         trust_i {seed-node 0.0}
         in_i {seed-node trust-injection}]
    (let [V_new (new-nodes-in g_i V_i)
          trust-diff (trust-change spreading-factor in_i) 
          trust_new (into (combine-maps trust_i trust-diff + 0.0 0.0)
                          (zipmap V_new (repeat (count V_new) 0.0)))
          max-change (apply max (vals trust-diff))
          g_new (add-virtual-nodes g_i seed-node V_new)]
      ;;(print "appleseed it" i "\n")
      (comment 
        (print "\tnew nodes: " V_new
               "\n\tmax-trust-change " max-change
               "\n\tinjection" in_i
               "\n\ttotal-trust assigned" (apply + (vals trust_new))
               "\n"))
      (if (and (<= max-change accuracy-threshold)
               (empty? V_new))
        trust_new
        (recur (inc i)
               g_new
               (into V_i V_new)
               trust_new
               (update-incoming in_i g_i spreading-factor V_new)
                     ))
      )))

(defn appleseed*
  "Apply more than one trust injection into a graph using appleseed.
  `graph` must be an `uber/digraph`
  `trust-injections` is a map from nodes to the seed score
  `spreading-factory` is a float between 0.0 and 1.0
  `accuracy-threshold` is a float larger than but close to 0.0. The bigger
    it is, the sooner each iteration will stop.
  "
  [graph trust-injections spreading-factor accuracy-threshold]
  (reduce (fn [acc  trust-map]
            (combine-maps acc trust-map + 0.0 0.0))
          (map (fn [seed-node]
                 (appleseed graph seed-node (get trust-injections seed-node)
                            spreading-factor accuracy-threshold))
               (keys trust-injections)))
  )
