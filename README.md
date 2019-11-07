# kg-disinfo

Knowledge Graph-based (lack of) credibility estimation.

Given a KG and a few seed nodes which *contain (lack of) credibility scores* (represented as a numerical value), 
the system uses a metric propagation algorithm to estimate the (lack of) credibility of neighbouring nodes (for which no previous score is available). 

The algorithm allows you to define weights based on relation types: this allows 
you to define which relations should propagate more or less of the disinformation metric.

## Installation

Not release yet.

## Set up
The project is built using [Leiningen](https://leiningen.org/), which is like maven, but for [Clojure](https://clojure.org/).

## Usage

### With standalone jar
If you already have a standalone jar for this project you can call

    $ java -jar kg-disinfo-0.1.0-standalone.jar [args]
    
e.g. 

    $ java -jar kg-disinfo-0.1.0-standalone.jar --help 
    
will print the command line arguments. Similarly

    $ java -jar kg-disinfo-0.1.0-standalone.jar --graph resources/dante-g-small.json --injections resources/dante-g-small-injection.json
    
will:
  * read a graph from the specified path
  * read an `injection` from the specified path 
  * execute the `appleseed` algorithm on the graph and the seed injection, using  `0.9` as the `spreading-factor`
  * write the resulting scored graph to `C:/tmp/dante-g_scored.pdf`
  
### Using leiningen
Either 1. build the standalone jar by calling
   
    $ lein uberjar
    
and follow instructions described above, or

2. Use the `run` option, e.g.:

    $ lein run -- --help 
    
This will do the same as running it with the standalone jar as described above.

### Via repl (or programammatically)
Please refer to the code for examples on how to do this.

## Specifying graphs and injections

### graphs
The easiest way to use `kg-disinfo` is to provide your graph in a json format. 
A (automatically generated) [example is provided](https://github.com/rdenaux/kg-disinfo/blob/master/resources/dante-g-small.json). 
The file must contain:
* a single javascript object with attribute `edges`, which must be a list of edge objects. 
* each `edge` object has fields:
** `src-node` *mandatory* a string identifier for the source node
** `tgt-node` *mandatory* a string identifier for the target node
** `meta` metadata for the edge with required fields:
*** `weight`: *mandatory* this is the propagation weight between the source and the target node and should be a float between 0.0 and 1.0. Values close to 0.0 mean that (lack of) credibility scores from the source node will not be propagated to target node. Values close to 1.0 mean that (lack of) credibility scores from the source node will be propagated to the target node.
*** `name`: *optional* the name of the relation

Note that the graphs are directed, therefore, you may need to add inverse edges if appropriate. This is needed because (mis)credibility may propagate in one direction but not in the inverse.

Here's a short sample of what graph edges should look like.

```json
{
  "edges" : [ {
    "src-node" : "digitalId_23585",
    "tgt-node" : "person_23578",
    "meta" : {
      "weight" : 1.0,
      "name" : "isOwner-1"
    }
  }, {
    "src-node" : "digitalId_23585",
    "tgt-node" : "resource_23592",
    "meta" : {
      "weight" : 0.9,
      "name" : "isAuthor"
    }
  } ]
}
```

### injections
In order to propagate (mis)credibility scores, you first need to tell us what are your initial ratings. This is done by the `injections` file. An [example is provided](https://github.com/rdenaux/kg-disinfo/blob/master/resources/dante-g-small-injection.json):

```json
{
  "injection" : {
    "resource_23586" : 1.0,
    "resource_23594" : 1.0
  }
}
```

This is a json file with a single object with property `injection`. The `injection` should be another object with as properties, the node identifiers used in the corresponding `graph`. The values are the initial (mis)credibility scores, they *must* be float values. They should be larger than 0.0. We recommend using values between 0.0 and 1.0. Ideally these will have been assigned by human raters.

## Current limitations and improvement ideas

 * It would be nice to extend the `appleseed` algorithm to accept a `spreading-factor` value that depends on the node type. This would allow us to control that some intermediate nodes should not keep (much of the) score, while other nodes will tend to *keep* most of the scores.
 * When using the CLI, the client needs to specify the propagation weights when providing the graph. We could expose a way to assign default weights based on relationship types, but this would require providing additional information as part of the `graph`. Function `kg-disinfo.generator.generate-test-graph` already implements similar functionality which could be exposed and used for this. Similarly, the ability to automatically add inverse-edges could be specified as part of the graph metadata.

## Bugs

 * No tests

## Acknowledgements

Originally developed as part of the [DANTE](https://www.h2020-dante.eu/) project and refined as part of the [Co-inform](https://coinform.eu) project.

## License

Copyright © 2019 Expert System Iberia
