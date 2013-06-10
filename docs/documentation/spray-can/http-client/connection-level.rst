.. _ConnectionLevelApi:

Connection-level API
====================

The connection-level API is the lowest-level client-side API *spray-can* provides. It gives you full control over when
HTTP connections are opened and closed and when requests are to be send across which connection. As such it offers the
highest flexibility at the cost of providing the least convenience.


Opening HTTP Connections
------------------------

With the connection-level API you open a new HTTP connection to a given host by sending an ``Http.Connect`` command
message to the ``Http`` extensions as such::

    IO(Http) ! Http.Connect("www.spray.io", port = 8080)

Apart from the host name and port the ``Http.Connect`` message also allows you to specify socket options and a larger
number of configuration settings for the connection.

Upon receipt of an ``Http.Connect`` message *spray-can* internally spawns a new ``HttpClientConnection`` actor that
manages a single HTTP connection across all of its lifetime. Your code never deals with the ``HttpClientConnection``
actor class directly, in fact it is marked ``private`` to the *spray-can* package. All communication with a connection
actor happens purely via actor messages, the majority of which are defined in the `spray.can.Http`_ object.

After a new connection actor has been started it tries to open a new TCP connection to the given endpoint and responds
with an ``Http.Connected`` event message to the sender of the ``Http.Connect`` command as soon as the connection has
been successfully established. If the connection could not be opened for whatever reason an ``Http.CommandFailed`` event
is being dispatched instead and the connection actor is stopped.

.. _spray.can.Http: https://github.com/spray/spray/blob/master/spray-can/src/main/scala/spray/can/Http.scala#L29


Request-Response Cycle
----------------------

Once the connection actor has responded with an ``Http.Connected`` event you can send it one or more *spray-http*
``HttpRequestPart`` messages. The connection actor will serialize them across the connection and wait for responses.
As soon as a response for a request has been received it is dispatched as a ``HttpResponsePart``
instance to the sender of the respective request.

After having received a response for a request the application can decide to send another request across the same
connection (i.e. to the same connection actor) or close the connection and (potentially) open a new one.


Closing Connections
-------------------

Unless some kind of error (or timeout) occurs the connection actor will never actively close an established connection,
even if the response contains a ``Connection: close`` header. The application can decide to actively close a connection
by sending the connection actor one of the ``Http.CloseCommand`` messages described in the chapter about
:ref:`CommonBehavior`.

Close notification events are dispatched to the senders of all requests that still have unfinished responses pending as
well as all actors that might have already sent ``Http.CloseCommand`` messages.


Timeouts
--------

If no response to a request is received within the configured ``request-timeout`` period the connection actor closes
the connection and dispatches an ``Http.Closed`` event message to the senders of all requests that are currently open.

If the connection is closed after the configured ``idle-timeout`` has expired the connection actor simply closes the
connection and stops itself. If the application would like to be notified of such events it should "watch" the
connection actor and react to the respective ``Terminated`` events (which is a good idea in any case).

In order to change the respective config setting *for this connection only* the application can send the following
messages to the connection actor:

- spray.io.ConnectionTimeouts.SetIdleTimeout
- spray.http.SetRequestTimeout