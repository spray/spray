.. _IOServer:

IOServer
========

The IOServer__ is a simple actor base class for server-side networking components.
It builds upon an :ref:`IOBridge` and provides server-side connection management. An ``IOServer`` cannot be used
directly but has to be subclassed (potentially mixing in the :ref:`ConnectionActors` trait) and augmented
with the custom application logic that actually does whatever the specific networking server is supposed to do.
In that sense the ``IOServer`` class merely provides the "boilerplate" logic common to most *spray-io* based network
servers.

__ source_

After having created an ``IOServer`` actor instance you typically send it a ``Bind`` message, which causes it to listen
for incoming connections on a specific interface/port. When the server is up the sender of the ``Bind`` receives a
``Bound`` event and subsequently a ``Connected`` event for every new connection that has been accepted.
Once bound the server can be unbound with an ``Unbind`` command.

In its original form, without mixing in the :ref:`ConnectionActors` trait, an ``IOServer`` designates itself as the
handler of all network events. You will therefore have to augment its ``receive`` behavior with your own logic
handling incoming ``Received`` events as well as generating the appropriate ``Send`` commands.


Examples
--------

One example of a network server based on the ``IOServer`` is the *spray-can* :ref:`HttpServer`. You might find
`its sources`__ quite readable.

__ https://github.com/spray/spray/blob/master/spray-can/src/main/scala/spray/can/server/HttpServer.scala

Another example is the ``echo-server`` implementation that can be found here__.
It presents a simple echo server that you can ``telnet`` to.

__ https://github.com/spray/spray/blob/master/examples/spray-io/echo-server/src/main/scala/spray/examples/Main.scala

To run it, simply check out the *spray* codebase and run ``sbt "project echo-server" run``.


Messaging Protocol
------------------

The convention in *spray* is to make all custom message types that a certain actor consumes or sends out available
in the actors companion object. In this regard the ``IOServer`` is no exception, you can find all commands and events
that an ``IOServer`` works with in `its companion object`__

__ source_
.. _source: https://github.com/spray/spray/blob/master/spray-io/src/main/scala/spray/io/IOServer.scala


