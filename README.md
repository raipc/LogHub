# LogHub

Loghub is a pipeline log, close to [logstash](https://www.elastic.co/products/logstash "Collect, Enrich & Transport Data"). But it's
written in java for improved stability and performance.

It received events from external sources, process them and send them.

All components are organized in many pipeline that can be interconnect. A pipeline goes from one receiver source
that generate events, send throught processor and forward them to a sender.

Receiver source uses decoders that takes bytes messages and generate a event from that.

Sender source uses decoders that take event and produce bytes message that are then send to the configured destination.

All of these five kind of operator (Receivers, Senders, Processors, Coders and Decoders) are java classes that can be derived for
custom usages.

Internally it uses [ØMQ](http://zeromq.org "Distributed Messaging") to forward events. Using it allows to avoid
any synchronized primitives while still using many threads for each processing step and so use all the cores of 
modern servers.

For configuration it uses a [DSL](https://en.wikipedia.org/wiki/Domain-specific_language "Domain specific langage") generated
using [antlr](http://www.antlr.org "ANother Tool for Language Recognition"). It's syntax is a strange mix of logstash configuration files,
java and a small tast of ruby. The exact grammar can be found at https://github.com/fbacchella/LogHub/blob/master/src/main/antlr4/loghub/Route.g4.

It look like:

    input {
        loghub.receivers.ZMQ {
            listen: "tcp://localhost:2120",
            decoder: loghub.decoders.Log4j
        }
    } | $main
    input {
        loghub.receivers.Udp {
            port: 2121
            decoder: loghub.decoders.Msgpack
        }
    } | $apache

    output $main | { loghub.senders.ElasticSearch }

    pipeline[apache] { loghub.processors.Geoip { datfilepath:"/user/local/share/GeoIP/GeoIP.dat", locationfield:"location", threads:4 } }
    pipeline[main] {
         loghub.processors.Log { threads: 2 } 
        | event.logger_name == "jrds.starter.Timer" || event.info > 4 ? loghub.processors.Drop  : ( loghub.processors.ParseJson | loghub.processors.Groovy { script: "println event['logger_name']" } ) 
    }
    extensions: "/usr/share/loghub/plugins:/usr/share/loghub/scripts"

This configuration define two receivers, one that listen using 0MQ for log4j events. The other listen for msgpack encoded events on a udp port,
like  some that can be generated by [mod_log_net](https://github.com/fbacchella/mod_log_net "An UDP logger for Apache").

The events received on UDP are send to one pipeline called "apache". All the events are transfered to the default "main" pipeline after resolving location
from visitors.

The log4j events are directly send to the main pipeline, that does some magic treatment on it. Pay attention to the test. It will be evaluated as a groovy scripts.

A property called "extensions" is defined. It allows to define custom extensions folders that will be used to resolve scripts and added to the class path.

In the configuration file, all the agent are defined using directly the class name.

If needed, slow or CPU bound processor can be given more dedicated threads by specifing a specific number of threads. They will be still one processor class instance,
but many threads will send events to it.
