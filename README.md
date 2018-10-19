# kg-disinfo

Knowledge Graph disinformation estimation.

Given a KG and a few seed nodes which *contain disinformation* (represented as a numerical value), 
the system uses a metric propagation algorithm to estimate how much neighbouring nodes should also
be mistrusted. The algorithm allows you to define weights based on relation types: this allows 
you to define which relations should propagate more or less of the disinformation metric.

Originally developed as part of the [DANTE](https://www.h2020-dante.eu/) project

## Installation

Not release yet.

## Set up
The project is built using [Leiningen](https://leiningen.org/), which is like maven, but for [Clojure](https://clojure.org/).

## Usage

### With standalone jar
If you already have a standalone jar for this project you can simply call

    $ java -jar kg-disinfo-0.1.0-standalone.jar [args]
    
e.g. 

    $ java -jar kg-disinfo-0.1.0-standalone.jar "C:/tmp/dante-g" 0.9
    
will:
  * generate a mock graph and write a pdf to `C:/tmp/dante-g_base.pdf`
  * generate a mock seed injection of disinformation for the graph and write it to `C:/tmp/dante-g_with_seed.pdf`
  * execute the `appleseed` algorithm on the graph and the seed injection, using  `0.9` as the `spreading-factor`
  * write the resulting scored graph to `C:/tmp/dante-g_scored.pdf`
  
    
### Using leiningen
Either 1. build the standalone jar by calling
   
    $ lein uberjar
    
and follow instructions described above, or

2. Use the `run` option, e.g.:

    $ lein run "C:/tmp/dante-g" 0.9
    
This will do the same as running it with the standalone jar as described above.

### Via repl (or programammatically)
You will need to import both the `kg-info.core`, `kg-info.dante-graphs` and `ubergraph.core` namespaces. 
E.g. assuming you are already in the `kg-info.core` namespace, you can:

``` clj
(ns kg-disinfo.core
  (:require [ubergraph.core :as uber]
            [kg-disinfo.dante-graphs :as dante-graphs]
            [clojure.string :as str])
  )

  (let [base-path "C:/tmp/dante-g"
        spreading-factor 0.9
        graph (dante-graphs/generate-dante-test-graph dante-graphs/dante-g-spec-small)
        seed-injections (dante-graphs/generate-injection
                                graph
                                (fn [node-id] (str/starts-with? node-id ":r_")))]
    (print "Storing base graph\n")
    (uber/viz-graph graph
                    {:save {:filename (str base-path "_base.pdf") :format :pdf}})
    (print "Storing base graph with injections\n")
    (uber/viz-graph (dante-graphs/color-by-scores graph seed-injections)
                    {:save {:filename (str base-path "_base_with_seed.pdf") :format :pdf}})
    (let [propagated-scores (time (appleseed* graph seed-injections spreading-factor 0.001))]
      (print "Storing scored graph")
      (uber/viz-graph (dante-graphs/color-by-scores graph propagated-scores)
                    {:save {:filename (str base-path "_base_scored.pdf") :format :pdf}})
      )
    )
  (print "done")
  )
```

### Improvements

 * Write unit tests (currently we have only tested the methods using a repl)
 * It would be nice to extend the `appleseed` algorithm to accept a `spreading-factor` value that depends on the node type. This would allow us to control that some intermediate nodes should not keep (much of the) score, while other nodes will tend to *keep* most of the scores.

### Bugs

 * No tests
 * We have not 

## License

Copyright © 2018 Expert System Iberia
