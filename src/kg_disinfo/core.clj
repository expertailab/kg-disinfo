(ns kg-disinfo.core
  (:require [ubergraph.core :as uber]
            [kg-disinfo.dante-graphs :as dante-graphs]
            [kg-disinfo.io :as kgio]
            [kg-disinfo.generator :as kgen]
            [kg-disinfo.appleseed :as appleseed]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:gen-class)
  )

(defn process-dante-graph-json
  "Accepts a dante-g-json string and produces a modified version
  with updated disinformation scores."
  [dante-g-json]
  (let [cfg {:ent-map-fn identity
             :rel-map-fn identity}
        dante-g (dante-graphs/parse-json-dante-graph dante-g-json)
        g (dante-graphs/create-from-dante-graph dante-g cfg)
        seed-injections (dante-graphs/read-seed-injections dante-g cfg)
        spreading-factor 0.9
        acc-threshold 0.001]
    (let [prop-scores (appleseed/appleseed* g seed-injections spreading-factor acc-threshold)]
      (dante-graphs/write-json-dante-graph (dante-graphs/update-dante-g dante-g prop-scores cfg))
      )))

(def cli-options
  [
   ["-g" "--graph FILE" "Path to a json file containing a graph description"
    :validate [#(.exists (io/file %)) "Graph file must exist"]]
   ["-i" "--injections FILE" "Path to a json file containing the initial scores"
    :validate [#(.exists (io/file %)) "Injections file must exist"]]
   ["-s" "--spreading-factor double" "Float value between 0 and 1"
    :default 0.85
    :parse-fn #(Double/parseDouble %)
    :validate [#(< 0.0 % 1.0) "Must be a number between (but excluding!) 0.0 and 1.0"]
    ]
   [nil "--accuracy-threshold double" "Float value between 0 and 0.1"
    :default 0.001
    :parse-fn #(Double/parseDouble %)
    :validate [#(< 0.0 % 0.1) "Must be a number betwee 0.0 and 0.1"]]
   [nil "--generate-viz format"
    "Format for generating the base, injected and scored graph visualizations. Options pdf, png, svg"
    :parse-fn #(keyword %)
    :validate [#(contains? #{:png :pdf :svg} %) "Must be either png, pdf or svg"]]
   ["-h" "--help"]
   ])

(defn- usage [options-summary prog-name]
  (->> [prog-name
        ""
        (str "Usage: " prog-name " [options]")
        ""
        "Options:"
        options-summary
        ""
        "Please refer to the documentation on github for more information."
        "Copyright 2018-2019 Expert System"]
       (clojure.string/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n\t"
       (str/join "\n\t" errors)))

(defn- cannot-execute-with-opts
  [options]
  (cond
    (not (:graph options)) "graph option is missing"
    (not (:injections options)) "injections option is missing"
    :default nil
    ))

(defn- validate-args
  "Validate command line arguments"
  [args prog-name cli-options]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
       errors
       {:exit-message (error-msg errors)}
       
       (not errors)
       (do
         (when (:help options)
           (log/info (usage summary prog-name)))         
         (let [err-msg (cannot-execute-with-opts options)]
           {:options options
            :exit-message (when err-msg ;; do not repeat help 
                            (str (when-not (:help options)  (usage summary prog-name))
                                 "\n" err-msg))
            :ok? (not err-msg)}))
       
       :else
       {:exit-message (usage summary prog-name)
        :ok? true}))
    )

(defn- sys-exit [status msg]
  (if (= 0 status)
    (log/info msg)
    (log/error msg))
  (System/exit status))

(defn- run
  [opts]
  (log/info (str  "Executing kg-dinsinfo with options" opts))
  (let [graph-path (:graph opts)
        graph (kgio/read-json-digraph graph-path)
        seed-injections (kgio/read-injection-json (:injections opts))
        spreading-factor (:spreading-factor opts)
        accuracy-threshold (:accuracy-threshold opts)
        base-path (str/replace graph-path ".json" "") ;;(.getParent (io/as-file graph-path))
        gen-viz (:generate-viz opts)
        ]
    (assert (< 0 (count seed-injections))
            (str "Must have at least one injection"))
    (assert (not-empty (clojure.set/intersection
                        (set (keys seed-injections))
                        (set (uber/nodes graph))))
            (str  "None of the injections are nodes in the graph. "
                  "Verify injection and graph files correspond to each other."))
    (assert (< 0.0 spreading-factor 1.0))
    (assert (< 0.0 accuracy-threshold 0.1))
    (when gen-viz
      (log/info "Storing base graph viz at" (str base-path "_base." (name gen-viz)))
      (uber/viz-graph graph
                      {:save {:filename (str base-path "_base." (name gen-viz)) :format gen-viz}}))
    (when gen-viz
      (log/info "Storing base graph with injections\n")
      (uber/viz-graph (dante-graphs/color-by-scores graph seed-injections)
                      {:save {:filename (str base-path "_base_with_seed." (name gen-viz)) :format gen-viz}}))
    (let [propagated-scores (time (appleseed/appleseed*
                                   graph seed-injections spreading-factor accuracy-threshold))]
      (when gen-viz
        (log/info "Storing scored graph")
        (uber/viz-graph (dante-graphs/color-by-scores graph propagated-scores)
                        {:save {:filename (str base-path "_base_scored." (name gen-viz)) :format gen-viz}}))
      (log/info "Storing updated scores at " (str base-path "_propagated.json"))
      (kgio/write-propagated-scores-json propagated-scores
                                         (str base-path "_propagated.json")
                                         {:pretty true})
      (log/info "Finished")
    )))

(defn -main
  "Reads the graph and injections from files given by the args and executes the injection propagation.
  Outputs a json file with the output scores as well as files with graph visualizations if requested."
  [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args "kg-disinfo" cli-options)]
    (if exit-message
      (sys-exit (if ok? 0 1)
                exit-message)
      (run options)
      ))
  (sys-exit 0 "done"))
