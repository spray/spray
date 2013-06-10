.. _CommonBehavior:

Common Behavior
===============

The *spray-can* :ref:`HTTP Server` and :ref:`HTTP Client APIs` share a number of command and event messages that are
explained in this chapter.


Closing Connections
-------------------

Server- and client-side connection actors can be sent one of three defined ``Http.CloseCommand`` messages in order to
trigger the closing of an HTTP connection. They mirror the `TCP-level commands and events from Akka IO`__ and have the
following semantics:

``Http.Close``
  A "regular" close. Potentially pending unsent data are flushed to the connection before a TCP FIN is sent.
  The peers FIN ACK is *not* awaited. If the close is successful the sender will be notified with an ``Http.Closed``
  event message.

``Http.ConfirmedClose``
  The closing of the connection is intially started by flushing pending writes and sending a TCP FIN to the peer.
  Data will continue to be received until the peer closes the connection too with its own FIN.
  If the close is successful the sender will be notified with an ``Http.ConfirmedClosed`` event message.

``Http.Abort``
  Immediately terminates the connection by sending a RST message to the peer. Pending writes are **not** flushed.
  If the close is successful the sender will be notified with an ``Http.Aborted`` event message.

In addition to the confirmation events mentioned above the connection actor will dispatch two other events derived from
the ``Http.ConnectionClosed`` trait in certain cases:

``Http.PeerClosed``
  Dispatched when the remote peer has closed the connection without "our" side having initiated the close first.

``Http.ErrorClosed``
  Dispatched whenever an error occurred that forced the connection to be closed.

__ http://doc.akka.io/docs/akka/2.2.0-RC1/scala/io-tcp.html#Closing_connections


.. _ACKed Sends:

ACKed Sends
-----------

If required the server- and client-side connection actors can confirm the successful delivery of an HTTP message (part)
to the OS network layer by replying with a "send ACK" message. The application can request a send ACK by modifying a
message part with the ``withAck`` method. For example, the following handler logic receives the String "ok" as an actor
message after the response has been successfully written to the connections socket:

.. includecode:: code/docs/HttpServerExamplesSpec.scala
   :snippet: acked-reply

Such ACK messages are especially helpful for triggering the sending of the next message part in a request- or response
streaming scenario since with such a design the application will never produce more data than the network can handle.

Send ACKs are always dispatched to the actor which sent the respective message (part).
They are only supported on the server-side as well as on the client-side connection-level API (i.e. not currently on the
client-side host- and request-level APIs).