(ns kg-disinfo.dante-graphs
  (:require  [ubergraph.core :as uber]
             [kg-disinfo.generator :as kgen]
             [cheshire.core :as cheshire]
             [clojure.string :as str]
             [clojure.java.io :as io]
             [clojure.tools.logging :as log]))

;; provides methods for generating random dante graphs which can be used as inputs for
;; the appleseed algorithm. Once we have access to the Dante KG we can implement methods for
;; converting (parts of) that graph into uber graphs suitable for appleseed

(def generated-inverse-rel-map
  {:isRelatedTo :isRelatedTo-1
   :isAuthor :isAuthor-1
   :isOwner :isOwner-1
   :isMember :isMember-1})

(def generated-default-reltype-weights
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
                               :fillcolor (calc-hex-color (get norm-scores n 0.0))} ))
            g (uber/nodes g)))
  )

(defn choose-from
  "returns a subset of `candidates`, of size approximating the requested `percentage`
  (of the total number of candidates."
  [candidates percentage]
  (let [total (count candidates)
        n (max 1 (int (* percentage total)))]
    (repeatedly n #(rand-nth candidates)))
  )

(defn generate-injection
  "Generates a seed injection for `g`.
  Returns a map from (a random selection of) nodes in g to a float seed value."
  ([g node-filter injection-factor]
   (assert (< 0.0 injection-factor 1.0) "injection-factor should be between 0 and 1")
   (let [filtered-nodes (filter node-filter (uber/nodes g))
         seeds (choose-from filtered-nodes injection-factor)]
     (zipmap seeds
             (repeat (count seeds) 1.0))))
  ([g node-filter]
   (generate-injection g node-filter 0.15))
  )

(def dante-g-spec-small
  {:nodes {:person 6 :group 3 :event 6 :resource 10 :digitalId 3 :object 3 :organization 1}
   :rel-counts {:isOwner 3 :isAuthor 6 :isRelatedTo 10
                :isMember 4 :involvesPerson 4
                :involvesDigitalIdentity 2
                :involvesGroup 3
                :involvesObject 3}
   :inverse-rel-map generated-inverse-rel-map
   :default-reltype-weights generated-default-reltype-weights})

(def dante-g-spec-med
  {:nodes {:person 60 :group 30 :event 60 :resource 100 :digitalId 30 :object 30 :organization 10}
   :rel-counts {:isOwner 30 :isAuthor 60 :isRelatedTo 100
                :isMember 40 :involvesPerson 40
                :involvesDigitalIdentity 20
                :involvesGroup 30
                :involvesObject 30}
   :inverse-rel-map generated-inverse-rel-map
   :default-reltype-weights generated-default-reltype-weights})

(def dante-g-spec-lar
  {:nodes {:person 600 :group 300 :event 600 :resource 1000 :digitalId 300 :object 300 :organization 100}
   :rel-counts {:isOwner 300 :isAuthor 600 :isRelatedTo 1000
                :isMember 400 :involvesPerson 400
                :involvesDigitalIdentity 200
                :involvesGroup 300
                :involvesObject 300}
   :inverse-rel-map generated-inverse-rel-map
   :default-reltype-weights generated-default-reltype-weights})

(defn generate-dante-test-graph
  [gen-spec]
  (kgen/generate-test-graph gen-spec))

(defn is-src-uiEnt
  [ui-ent ui-rel]
  ;; it'd be simpler to check by id, but sample data we have has all values 0
  (let [snid (-> ui-rel :danteg/relation :danteg/startNodeUUid)
        enui (-> ui-ent :danteg/convertibleToCard :danteg/uuid)]
    ;(println "snid: " snid)
    ;(println "enui: " enui)
    (= snid enui)))

(defn is-tgt-uiEnt
  [ui-ent ui-rel]
  (= (-> ui-rel :danteg/relation :danteg/endNodeUuid)
     (-> ui-ent :danteg/convertibleToCard :danteg/uuid))
  )

(defn- find-related-uiEnt-by-fn
  [ui-rel dante-g match-fn rel-label]
  (let [matching-ui-ents 
        (filter #(match-fn % ui-rel) (:danteg/uiEntitiesList dante-g))]
    (case (count matching-ui-ents)
      0 (throw (IllegalArgumentException. (str "Failed to find " rel-label " entity for " ui-rel)))
      1 (first matching-ui-ents)
      (do
        (log/warn "Multiple matches found for " rel-label " entity in " ui-rel)
        (first matching-ui-ents))
      ))
  )

(defn- find-related-uiEnt
  [ui-rel dante-g rel-type]
  (case rel-type
    :src (find-related-uiEnt-by-fn ui-rel dante-g is-src-uiEnt "src")
    :tgt (find-related-uiEnt-by-fn ui-rel dante-g is-tgt-uiEnt "tgt")
    :default (throw (IllegalArgumentException. (str "Invalid rel-type " rel-type))))
  )

(def danteg-inverse-rel-map
  {"#isOwner" "#ownedBy"
   "#isBrother" "#isBrother"
   "#follow" "#followedBy"
   "#isAuthor" "#isAuthorOf"
   "#like" "#likedBy"})

(def danteg-default-reltype-weights
  {"#isOwner" {:weight 1.0 ; person 2 digid
               :comment (str  "If a person is deemed untrustworthy, "
                              "their digital accounts should also be deemed untrustworthy")}
   "#ownedBy" {:weight 1.0 ; digid 2 person
               :comment (str "If a digid is disinforming, "
                             "their operators should also be distrusted")}
   "#isInvolved" {:weight 0.1 ; digid 2 event
                  :comment (str "If an untrustworthy digitalId is involved in an event,  "
                                "it's not necessarily the case that the event is untrustworthy, "
                                "it depends on where the event was extracted from.")}
   "#isAuthor" {:weight 1.0 ; resource (video text) 2 digid
                :comment (str  "If a resource is disinformative, "
                               "the author(s) should also be deemed disinformative")}
   "#isAuthorOf" {:weight 1.0 ; digid 2 resource
                  :comment (str  "If a digid is disinformative, "
                                 "all the resources produced by that author should also be mistrusted.")}
   "#follow" {:weight 0.4 ; digid 2 digid
              :comment (str "If a digid1 is disinforming and follows digid2, "
                            "digid2 is not necessarily disinforming")}
   "#followedBy" {:weight 0.7 ; digid 2 digid
                  :comment (str  "if a digid1 is disinforming, "
                                 "all those who follow it are also potentially disinforming.")}
   "#like" {:weight 0.6 ; digid 2 digid
            :comment (str "If digid1 is disinforming and likes digid2, "
                          "digid2 should also be mistrusted somewhat. ")}
   "#likedBy" {:weight 0.6 ; digid 2 digid
               :comment (str "If digid1 is disinforming and is liked by digid2, "
                             "digid2 should also be mistrusted.")}
   "#isBrother" {:weight 0.65 ; person 2 person
               :comment (str "If personA is disinforming and personB is his brother, "
                             "personB should also be somewhat mistrusted.")}
   "#isFounder" {:weight 0.9 ; person 2 org
                 :comment (str "If personA is disinforming and founded orgB, "
                              "that organization should also be mistrusted.")}
   "#isTerroristicEvent" {:weight 0.1 ; person 2 event
                 :comment (str "If personA is disinforming and is (involved?) in a terroristic event, "
                              "that event should not be mistrusted.")}
   })

(defn calc-ui-rel-weight
  [ui-rel]
  (let [relcat (-> ui-rel :danteg/relation :danteg/relationCategory)
        relmeta (get danteg-default-reltype-weights relcat)]
    (if relmeta
      (:weight relmeta)
      (do
        (log/warn "Unknown relation category " relcat " for relation " ui-rel
                  "\nUsing default propagation weight")
        0.5))))

(defn dante-g-to-uber-edges
  "Converts a valid `:danteg/dante-graph` data structure into a vector of
  uber edges (i.e. each edge is a triple [source-node dest-node edge-attribs].
  The source nodes are `:danteg/uiEntity` and the edge attribs are a map with
  keys `:weight`, `:uiRelation` and `:name`"
  [dante-g cfg]
  (let [uiRels (:danteg/uiRelationList dante-g)
        ent-map-fn (:ent-map-fn cfg)
        rel-map-fn (:rel-map-fn cfg)]
    (assert ent-map-fn)
    (assert rel-map-fn)
    (map (fn [ui-rel]
           [(ent-map-fn (find-related-uiEnt ui-rel dante-g :src))
            (ent-map-fn (find-related-uiEnt ui-rel dante-g :tgt))
            {:weight (calc-ui-rel-weight ui-rel)
             :name (-> ui-rel :danteg/relation :danteg/relationCategory)
             :uiRelation (rel-map-fn ui-rel)}]
           )
         uiRels)
    ))

(defn parse-json-dante-graph
  [json-str]
  (cheshire/parse-string json-str (fn [key] (keyword (str "danteg/" key)))))

(defn write-json-dante-graph
  [dante-g]
  (cheshire/generate-string dante-g {:key-fn
                                     (fn [key]
                                       (str/replace key ":danteg/" ""))
                                     })
  )

(defn read-ent-seed-injection
  [ui-ent]
  (let [disinfo-score (:danteg/disinformationScore ui-ent)
        content-dscore (or (-> ui-ent :danteg/convertibleToCard :danteg/contentDisinformationScore) 0.0)
        user-dscore (or    (-> ui-ent :danteg/convertibleToCard :danteg/userDisinformationScore) 0.0)
        seed (+ content-dscore user-dscore)]
    (log/trace "for ent: " (-> ui-ent :danteg/convertibleToCard :danteg/uuid)
              "\n disinfo-score " disinfo-score
              "\n user-dscore " user-dscore
              "\n content-dscore " content-dscore
              "\n seed " seed)
    (if (and (< disinfo-score 0.0)
             (> seed 0.0))
      seed
      nil
      )))

(defn read-seed-injections
  "Returns the seed injections from a pre-existing dante-g."
  [dante-g cfg]
  (into {}
        (map (fn [uiEnt]
               (let [seed (read-ent-seed-injection uiEnt)
                     ent-map-fn (:ent-map-fn cfg)]
                 (assert ent-map-fn)
                 (if (and seed (> seed 0.0))
                   [(ent-map-fn uiEnt) seed]
                   nil)))
             (:danteg/uiEntitiesList dante-g)))
  )

(defn calc-inversion-cfg
  [cfg]
  (log/info "calculating inversion-cfg for cfg with keys " (keys cfg))
  (let [inv-relmap (get cfg :inverse-rel-map danteg-inverse-rel-map)
        rel-weights (get cfg :default-reltype-weights danteg-default-reltype-weights)]
    {:inverse-rel-map inv-relmap
     :default-reltype-weights rel-weights
     }))

(defn create-from-dante-graph
  "Creates a `uber/digraph` from a Dante graph.
  `dante-g` can be a path to a json file or a valid `:danteg/dante-graph`
  data structure"
  ([dante-g cfg]
   (cond
     (string? dante-g) ;;
     (let [json (if (.exists (io/as-file dante-g))
                  (parse-json-dante-graph (slurp dante-g))
                  dante-g)]
       (create-from-dante-graph json cfg))
     
     (map? dante-g) ;; assume it's the agreed Dante graph datastructure
     (let [E (dante-g-to-uber-edges dante-g cfg)
           inversion-cfg (calc-inversion-cfg cfg)
           ]
       (apply uber/digraph (kgen/add-inverse-edges E inversion-cfg)))
     
     :else (throw
            (IllegalArgumentException. (str  "Cannot convert a " (type dante-g)
                                     " into a dante graph.")))))
  ([dante-g]
   (create-from-dante-graph dante-g
                            {:ent-map-fn identity
                             :rel-map-fn identity})))

(defn update-dante-g
  [dante-g disinfo-scores cfg]
  (into (dissoc dante-g :danteg/uiEntitiesList :danteg/uiRelationList)
        {:danteg/uiEntitiesList
         (into [] 
               (map (fn [ui-ent]
                      (let [ent-map-fn (:ent-map-fn cfg)
                            ui-ent-as-key (ent-map-fn ui-ent)
                            score (get disinfo-scores ui-ent-as-key)
                            ]
                        (into (dissoc ui-ent :danteg/disinformationScore)
                              {:danteg/disinformationScore score})
                        ))
                    (:danteg/uiEntitiesList dante-g)))
         :danteg/uiRelationList (:danteg/uiRelationList dante-g)}
        ))
