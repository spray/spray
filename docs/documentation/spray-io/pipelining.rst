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

Pipeline stages form the entities, into which you typically structure your client or server logic. There are four
types of stages:

- CommandPipelineStage_
- EventPipelineStage_
- DoublePipelineStage_
- EmptyPipelineStage_

The central element of all pipeline stages is the ``build`` method, which is defined like this::

    def build(context: PipelineContext,
              commandPL: Pipeline[Command],
              eventPL: Pipeline[Event]): BuildResult

whereby ``Pipeline`` is the following simple type alias::

    type Pipeline[-T] = T => Unit

So, when seen from the outside a pipeline appears simply as a sink for messages of specific type.

The ``build`` method of a pipeline stage is called every time a new connection actor is created. Apart from the
``PipelineContext``, which is defined like this:

.. includecode:: /../spray-io/src/main/scala/cc/spray/io/pipelining/Pipelines.scala
   :snippet: pipeline-context

the ``build`` method receives its downstream "tail" pipelines as arguments.
The result of the ``build`` method depends on the type of the pipeline stage. When called by the connection actor
it produces the next

- pair of command and event pipelines (for ``DoublePipelineStage`` instances and the ``EmptyPipelineStage``)
- command pipeline (for ``CommandPipelineStage`` instances)
- event pipeline (for ``EventPipelineStage`` instances)


CommandPipelineStage
~~~~~~~~~~~~~~~~~~~~

.. compound::
   .. image:: /images/CommandPipelineStage.svg

A ``CommandPipelineStage`` is an element of the command pipeline. It receives ``Command`` message from its predecessor
(i.e. upstream) stage in the command pipeline and has access to both downstream pipeline "tails".
Since it is not "chained into" the event pipeline it cannot see the event stream. However, it can generate
events and "push" them into the downstream part of the event pipeline.


EventPipelineStage
~~~~~~~~~~~~~~~~~~

.. compound::
   .. image:: /images/EventPipelineStage.svg

An ``EventPipelineStage`` is an element of the event pipeline. It receives ``Event`` message from its predecessor
(i.e. upstream) stage in the event pipeline and has access to both downstream pipeline "tails".
Since it is not "chained into" the command pipeline it cannot see the command stream. However, it can generate
command and "push" them into the downstream part of the command pipeline.


DoublePipelineStage
~~~~~~~~~~~~~~~~~~~

.. compound::
   .. image:: /images/DoublePipelineStage.svg

A ``DoublePipelineStage`` is an element of the both pipelines. It receives ``Command`` and ``Event`` message from its
predecessor (i.e. upstream) stages and has access to both downstream pipeline "tails".
It can generate both, commands and events, and "push" them into the downstream part of the respective pipeline.


EmptyPipelineStage
~~~~~~~~~~~~~~~~~~

The ``EmptyPipelineStage`` singleton object is never part of any pipeline an cannot be derived from.
It merely serves as a "neutral" element when `combining pipeline stages`_.


Execution Model
---------------

Since pipelines are simple functions ``T => Unit`` (with ``T`` being either ``Command`` or ``Event``) each stage is
in complete control of the message flow. It can not only modify messages, it can also hold, discard or multiply them in
any way. Additionally it can generate messages of the opposite type and push them into the respective downstream tail
pipeline. For example, the RequestParsing__ EventPipelineStage of the :ref:`spray-can` ``HttpServer`` generates commands
that complete a request with an error response whenever a request parsing error is encountered.

Also, all pipeline code is always executed in the context of the connection actor and therefore isolated to a specific
connection. As such, keeping mutable, connection-specific state within a pipeline stage is not a problem.

When another actor gets a hold of the connection actors ``ActorRef`` (e.g. because a pipeline stage sent an
``IOPeer.Tell`` command using the connection actor as ``sender``) and itself sends a message to the connection actor,
this message hits the connection actors ``receive`` behavior, which is defined like this:

.. includecode:: /../spray-io/src/main/scala/cc/spray/io/ConnectionActors.scala
   :snippet: receive

As you can see the connection actor feeds all incoming messages directly into its respective pipeline. This behavior can
also be useful from within a pipeline stage itself, because it allows any stage to push a command or event into the
*beginning* of the respective pipeline, rather than just its own downstream pipeline "tail". All that stage has to do is
to send the message to its own connection actor.

__ https://github.com/spray/spray/blob/master/spray-can/src/main/scala/cc/spray/can/server/RequestParsing.scala


Creating Pipeline Stages
------------------------

Since the pipeline stage types outlined above are regular Scala traits you can implement them in any way you like.
However, the following template, which illustrates how pipeline stage implementations within *spray* itself are
structured, might give you a good starting point::

    object PipelineStageName {

      // members defined here are global across
      // all server and client instances

      def apply(<arguments>): PipelineStage = new DoublePipelineStage {
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
              // handle "interesting" commands, sent commands
              // and events to the commandPL or eventPL

            case cmd => // pass through all "unknown" commands
              commandPL(cmd)
          }

          val eventPipeline: Pipeline[Event] = {
            case ... =>
              // handle "interesting" events, sent commands
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



This template shows a full ``DoublePipelineStage``. Command- and EventPipelineStages can be created in a very similar
although slightly simpler manner. Check out the ResponseRendering__ stage of the :ref:`spray-can` ``HttpServer`` as an
example of a ``CommandPipelineStage``, or the TickGenerator__ as an ``EventPipelineStage`` example.

__ https://github.com/spray/spray/blob/master/spray-can/src/main/scala/cc/spray/can/server/ResponseRendering.scala
__ https://github.com/spray/spray/blob/master/spray-io/src/main/scala/cc/spray/io/pipelining/TickGenerator.scala


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
expression before the ``?`` is false. The ``EmptyPipelineStage`` does not create any pipeline segments when the
command and event pipelines are built for a new connection, which is why "switched off" PipelineStages do not introduce
any overhead.

.. _HttpClient: https://github.com/spray/spray/blob/master/spray-can/src/main/scala/cc/spray/can/client/HttpClient.scala


The Final Stages
----------------

Both pipelines, the command- as well as the event pipeline, are always terminated by stages provided by the connection
actor itself. The following, an except of the `IOConnectionActor sources`__, is their definition:

__ https://github.com/spray/spray/blob/master/spray-io/src/main/scala/cc/spray/io/ConnectionActors.scala

.. includecode:: /../spray-io/src/main/scala/cc/spray/io/ConnectionActors.scala
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


FAQ
---

Why not simply always use DoublePipelineStages?
  You might ask yourself why *spray-io* differentiates between Command-, Event- and DoublePipelineStages when everything
  that can be done by Command- and EventPipelineStages can also be achieved by DoublePipelineStages alone.
  The reason is twofold. Firstly, choosing the "right" stage type for a piece of logic makes it easier to understand
  your code, without having to read it all. And secondly, by implementing "only" a CommandPipelineStage when your logic
  doesn't require access to the event stream keeps the event pipeline shorter, thereby reducing overhead. The same is
  true vice versa for the EventPipelineStage.
