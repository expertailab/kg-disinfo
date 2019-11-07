(ns danteg
  "Defines a spec for the Dante graphs"
  (:require [clojure.spec.alpha :as s]))

(s/def ::targetEntity string?)
(s/def ::pid string?) ;;UUID?
(s/def ::date string?)
(s/def ::id int?)

(s/def ::typed
  (s/keys :req-un [::type]))

(s/def ::disinformationScore float?)

(s/def ::person
  (s/keys :req-un [::type
                   ]))
(s/def ::resource
  (s/or :video ::video
        :image ::image
        :text  ::text))

(s/def ::convertibleToCard
  (s/and 
   (s/keys :req-un [::type])
   (s/or :person ::person
         :event  ::event
         :org    ::organization
         :resource ::resoure
         :digitalId ::digitalIdentity
         )))

(s/def ::uiEntity
  (s/keys :req-un [::id ::convertibleToCard ::disinformationScore]))

(s/def ::uiEntitiesList (s/+ ::uiEntity))

(s/def ::relationUuid string?)
(s/def ::relationType (= "relation"))
(s/def ::relationCategory
  (s/and string?
         (s/or
          :isBrother   #(= % "#isBrother") ;; person 2 person
          :isOwner     #(= % "#isOwner") ;; person 2 digid
          :isInvolved  #(= % "#isInvolved"); digid 2 event
          :isAuthor    #(= % "#isAuthor"); (or video text) 2 digid
          :follow      #(= % "#follow"); digid 2 digid 
          :like        #(= % "#like"); digid 2 digid
               )))
(s/def ::startNodeUUid string?)
(s/def ::endNodeUuid string?)

(s/def ::relation
  (s/keys :req-un [::relationUuid ::relationType
                   ::relationCategory
                   ::startNodeUUid
                   ::endNodeUuid])
  )
(s/def ::uiRelation
  (s/keys :req-un [::source ::target ::relation]))

(s/def ::uiRelationList (s/+ ::uiRelation))

(s/def ::dante-graph
  (s/keys :req-un [::targetEntity ::pid
                   ::date
                   ::uiEntititesList
                   ::uiRelationList]))

