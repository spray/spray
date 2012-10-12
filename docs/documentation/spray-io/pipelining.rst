.. _pipelining:

Pipelining
==========

In order to form a proper foundation for architecturally sound network client- and server implementations *spray-io*
supports *pipelining*. The basic idea is to design a client or server as a series of loosely coupled *pipeline stages*,
with each stage implementing only one tightly scoped aspect of the whole logic.
Pipeline stages can be assembled into different configurations in way that is configurable at runtime, which allows
a user of the client or server to enable or disable selected parts according to their needs.

The *spray-io* pipelining architecture is loosely based on the one from Netty_. However, in contrast to its Java-based
"role model" it can leverage Scala language features such as pattern matching for a cleaner and more concise
implementation without sacrificing performance.

.. _netty: http://www.jboss.org/netty


Architecture
------------

The following diagram outlines the major concepts:

.. image:: /images/pipelining.svg

When a connection actor is started by its parent (which is either an :ref:`IOClient` or an :ref:`IOServer`) it
immediately constructs two "pipelines", a *command pipeline* and an *event pipeline*. A pipeline consists of one or
more *pipeline stages*, through which messages travel unidirectionally.

In the command pipeline ``Command`` messages are passed from higher-level stages down to lower-level stages until they
hit the final stage, which, in most cases, passes them on to the underlying :ref:`IOBridge`. In the event pipeline
``Event`` message flow in the other direction, from the :ref:`IOBridge` up through all its stages.

Pipeline stages form the entities, into which you typically structure your client or server logic. Conceptually there
are four types of stages:

- "Command-only stages", which inject logic only into the command pipeline
- "Event-only stages", which inject logic only into the event pipeline
- "Full stages", which inject logic into both pipelines
- "Empty stages", which don't add any logic (they serve as neutral element for pipeline combination)


The *PipelineStage* trait
-------------------------

Pipeline stages are modelled by the ``PipelineStage`` trait, whose central element is the ``build`` method::

    def build(context: PipelineContext,
              commandPL: Pipeline[Command],
              eventPL: Pipeline[Event]): Pipelines

whereby ``Pipeline`` is the following simple type alias::

    type Pipeline[-T] = T => Unit

So, when seen from the outside, a pipeline appears simply as a sink for messages of specific type.

The ``build`` method of a ``PipelineStage`` is called every time a new connection actor is created. Apart from the
``PipelineContext`` the ``build`` method receives its downstream "tail" pipelines as arguments.
The result of the ``build`` method is an instance of the ``Pipelines`` trait, which simply groups together the new
command and event pipelines, after the stage has prepended them with its own logic:

.. includecode:: /../spray-io/src/main/scala/spray/io/Pipelines.scala
   :snippet: pipelines

The dotted lines in the following diagram illustrate what the ``build`` method returns:

.. image:: /images/PipelineStage.svg


Execution Model
---------------

Since pipelines are simple functions ``T => Unit`` (with ``T`` being either ``Command`` or ``Event``) each stage is
in complete control of the message flow. It can not only modify messages, it can also hold, discard or multiply them in
any way. Additionally it can generate messages of the opposite type and push them into the respective downstream tail
pipeline. For example, the RequestParsing__ stage of the :ref:`spray-can` :ref:`HttpServer` generates commands
that complete a request with an error response whenever a request parsing error is encountered.

Also, all pipeline code is always executed in the context of the connection actor and therefore isolated to a specific
connection. As such, keeping mutable, connection-specific state within a pipeline stage is not a problem.

When another actor gets a hold of the connection actors ``ActorRef`` (e.g. because a pipeline stage sent an
``IOPeer.Tell`` command using the connection actor as ``sender``) and itself sends a message to the connection actor,
this message hits the connection actors ``receive`` behavior, which is defined like this:

.. includecode:: /../spray-io/src/main/scala/spray/io/ConnectionActors.scala
   :snippet: receive

As you can see the connection actor feeds all incoming ``Command`` or ``Event`` messages directly into its respective
pipeline. This behavior can also be useful from within a pipeline stage itself, because it allows any stage to push a
command or event into the *beginning* of the respective pipeline, rather than just its own downstream pipeline "tail".
All that stage has to do is to send the message to its own connection actor.

__ https://github.com/spray/spray/blob/master/spray-can/src/main/scala/spray/can/server/RequestParsing.scala


Creating Pipeline Stages
------------------------

Since the ``PipelineStage`` trait is a regular Scala trait you can implement it in any way you like. However, the
following template, which illustrates how pipeline stage implementations within *spray* itself are
structured, might give you a good starting point::

    object PipelineStageName {

      // members defined here are global across
      // all server and client instances

      def apply(<arguments>): PipelineStage = new PipelineStage {
        require(...) // argument verification

        // members defined here exist once per
        // server or client instance

        def build(context: PipelineContext,
                  commandPL: Pipeline[Command],
                  eventPL: Pipeline[Event]): Pipelines = new Pipelines {

          // members defined here exist
          // once per connection

          val commandPipeline: Pipeline[Command] = {
            case ... =>
              // handle "interesting" commands, send commands
              // and events to the commandPL or eventPL

            case cmd => // pass through all "unknown" commands
              commandPL(cmd)
          }

          val eventPipeline: Pipeline[Event] = {
            case ... =>
              // handle "interesting" events, send commands
              // and events to the commandPL or eventPL

            case ev => // pass through all "unknown" events
              eventPL(ev)
          }
        }
      }

      ////////////// COMMANDS and EVENTS //////////////

      // definition of all commands and events specific to this pipeline stage

      case class MyCommand(...) extends Command
      case class MyEvent(...) extends Event
    }



This template shows a "full stage``, with logic injected into both pipelines. If your stage only requires logic in one
of the pipelines simply pass through the other one unchanged. For example, if your stage is a "command-only" stage you'd
implement the ``eventPipeline`` member of the ``Pipelines`` trait as such::

    val eventPipeline = eventPL

Check out the ResponseRendering__ stage of the :ref:`spray-can` :ref:`HttpServer` as an
example of a "command-only stage" and the :ref:`TickGenerator` as an "event-only stage" example.

__ https://github.com/spray/spray/blob/master/spray-can/src/main/scala/spray/can/server/ResponseRendering.scala


Combining Pipeline Stages
-------------------------

Two ``PipelineStage`` instances can be combined into single one with the ``>>`` operator. Additionally an expression
creating a ``PipelineStage`` can be made optional by prepending it with a ``<boolean> ?`` modifier.

To understand what this means check out this simplified version of the definition of the :ref:`spray-can`
`HttpClient`_ pipeline::

    ClientFrontend(...) >>
    (ResponseChunkAggregationLimit > 0) ? ResponseChunkAggregation(...) >>
    ResponseParsing(...) >>
    RequestRendering(...) >>
    (settings.IdleTimeout > 0) ? ConnectionTimeouts(...) >>
    SSLEncryption ? SslTlsSupport(...) >>
    (ReapingCycle > 0 && IdleTimeout > 0) ? TickGenerator(ReapingCycle)

This expression constructs a single ``PipelineStage`` instance from 3 to 7 sub-stages, depending on the configuration
settings of the client. The lines containing a ``?`` operator evaluate to ``EmptyPipelineStage`` if the boolean
expression before the ``?`` is false. The ``EmptyPipelineStage`` singleton object serves as a "neutral" element when
combining pipeline stages. Its ``build`` method doesn't append any logic to either pipeline, so "switched off"
PipelineStages do not introduce any overhead.

.. _HttpClient: https://github.com/spray/spray/blob/master/spray-can/src/main/scala/spray/can/client/HttpClient.scala


The Final Stages
----------------

Both pipelines, the command as well as the event pipeline, are always terminated by stages provided by the connection
actor itself. The following, an excerpt of the `IOConnectionActor sources`__, is their definition:

__ https://github.com/spray/spray/blob/master/spray-io/src/main/scala/spray/io/ConnectionActors.scala

.. includecode:: /../spray-io/src/main/scala/spray/io/ConnectionActors.scala
   :snippet: final-stages

The final stage of the command pipeline translates most of the defined messages into their ``IOBridge`` counterparts
and sends them off to the bridge. There is one command, ``IOPeer.Tell``, which does not follow this pattern.
This command simply encapsulates an Actor ``tell`` call into a ``Command`` message. Whenever a pipeline stage would like
to send a message to an actor it should push an ``IOPeer.Tell`` command into the command pipeline rather than
calling ``actorRef.tell`` directly. This design has two benefits:

- Other downstream pipeline stages can react to, and maybe even modify the ``Tell``.
- The stage remains independently testable, without the need to fire up actors. (Check out the :ref:`testing-pipelines`
  chapter for more info on this.)

The final stage of the event pipeline only reacts to ``Closed`` messages. It stops the connection actor as a result.
