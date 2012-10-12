.. _IOClient:

IOClient
========

The IOClient__ is a simple actor base class for client-side networking components.
It builds upon an :ref:`IOBridge` and provides client-side connection management. An ``IOClient`` cannot be used
directly but has to be subclassed (potentially mixing in the :ref:`ConnectionActors` trait) and augmented
with the custom application logic that actually does whatever the specific networking client is supposed to do.
In that sense the ``IOClient`` class merely provides the "boilerplate" logic common to most *spray-io* based network
clients.

__ sources_

After having created an ``IOClient`` actor instance you typically send it a ``Connect`` message, which is either
responded to with a ``Connected`` event after the connection has been established, or a ``Status.Failure`` message
(which is automatically turned into Future failures, if the ``Connect`` was sent with an ``ask``).

In its original form, without mixing in the :ref:`ConnectionActors` trait, an ``IOClient`` designates itself as the
handler of all network events. You will therefore have to augment its ``receive`` behavior with your own logic
generating the appropriate ``Send`` commands as well as handling incoming ``Received`` events.


Examples
--------

One example of a network client based on the ``IOClient`` is the *spray-can* :ref:`HttpClient`. You might find
`its sources`__ quite readable.

__ https://github.com/spray/spray/blob/master/spray-can/src/main/scala/spray/can/client/HttpClient.scala

Another (admittedly very contrived and overly simplified) example is presented here:

.. includecode:: code/docs/IOClientExamplesSpec.scala
   :snippet: example-1

Note than this example uses blocking calls to wait for future results, which is something that you probably do not
want to do in a performance-sensitive part of your application. Also, for brevity reasons the example omits all
error handling logic.


Messaging Protocol
------------------

The convention in *spray* is to make all custom message types that a certain actor consumes or sends out available
in the actors companion object. In this regard the ``IOClient`` is no exception, you can find all commands and events
that an ``IOClient`` works with in `its companion object`__

__ sources_
.. _sources: https://github.com/spray/spray/blob/master/spray-io/src/main/scala/spray/io/IOClient.scala


