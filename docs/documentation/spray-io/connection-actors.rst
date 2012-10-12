.. _ConnectionActors:

ConnectionActors
================

The ConnectionActors__ trait can be mixed into either an :ref:`IOClient` or an :ref:`IOServer` and changes their
behavior to create a fresh ``IOConnectionActor`` for every newly established connection. These "connection actors"
encapsulate connection-specific state and serve as handlers for all events coming in from the underlying
:ref:`IOBridge`.

Theoretically, in order to implement you own client- or server logic, it would suffice if *spray-io* gave you the
ability to somehow place your own ``Actor.Receive`` partial function directly in these connection actors.
However, implementing non-trivial client- or server logic in a single, monolithic actor usually doesn't yield very
readable and maintainable code. Rather an architecturally clean implementation would split up the logic into different,
loosely coupled chunks, each handling only one very tightly scoped aspect of the whole client or server.

*spray-io* provides an infrastructure for this type of architecture with the concept of :ref:`pipelining`, for which
the ConnectionActors__ trait forms the basis.

__ sources_
__ sources_
.. _sources: https://github.com/spray/spray/blob/master/spray-io/src/main/scala/spray/io/ConnectionActors.scala