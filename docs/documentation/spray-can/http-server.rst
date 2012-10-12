.. _HttpServer:

HttpServer
==========

The *spray-can* ``HttpServer`` is an embedded, low-level, low-overhead, high-performance, fully asynchronous,
non-blocking and actor-based HTTP/1.1 server implemented on top of :ref:`spray-io`.

It sports the following features:

- Low per-connection overhead for supporting many thousand concurrent connections
- Efficient message parsing and processing logic for high throughput applications (> 40K requests/sec on ordinary
  consumer hardware)
- Full support for `HTTP persistent connections`_
- Full support for `HTTP pipelining`_
- Full support for asynchronous HTTP streaming (i.e. "chunked" transfer encoding)
- Optional SSL/TLS encryption
- Actor-based architecture for easy integration into your Akka applications

.. _HTTP persistent connections: http://en.wikipedia.org/wiki/HTTP_persistent_connection
.. _HTTP pipelining: http://en.wikipedia.org/wiki/HTTP_pipelining


Design Philosophy
-----------------

The *spray-can* ``HttpServer`` is scoped with a clear focus on the essential functionality of an HTTP/1.1 server:

- Connection management
- Message parsing and header separation
- Timeout management (for requests and connections)
- Response ordering (for transparent pipelining support)

All non-core features of typical HTTP servers (like request routing, file serving, compression, etc.) are left to
the next layer in the application stack, they are not implemented by *spray-can* itself.
Apart from general focus this design keeps the server small and light-weight as well as easy to understand and
maintain. It also makes a *spray-can* ``HttpServer`` a perfect "container" for a :ref:`spray-routing` application,
since *spray-can* and *spray-routing* nicely complement and interface into each other.


Basic Architecture
------------------

The *spray-can* ``HttpServer`` is implemented as an Akka actor, which talks to an underlying :ref:`IOBridge` and spawns
new child actors for every new connection. These connection actors process the requests coming in across the connection
and dispatch them as immutable :ref:`spray-http` ``HttpRequest`` instances to a "handler" actor provided by the
application. The handler completes a request by simply replying with an ``HttpResponse`` instance:

.. includecode:: code/docs/HttpServerExamplesSpec.scala
   :snippet: example-1

Additionally the handler is informed of the closing of connections, the successful sending of responses (optionally)
as well as any errors occurring on the network side (the details of which are explained in the `Message Protocol`_
section below).

The following types of handlers are supported:

Singleton Handlers
  The application dedicates a single, long-lived actor for handling all requests.

Per-Connection Handlers
  The application provides a new handler actor for every new incoming connection.

Per-Message Handlers
  The application provides a new handler actor for every new incoming request.


Starting and Stopping
---------------------

Since the ``HttpServer`` is a regular actor it is started and stopped like any other one.
The :ref:`simple-http-server` example contains a complete and working code example.


Message Protocol
----------------

A running ``HttpServer`` actor understands the following command messages
(they are all defined in the HttpServer__ companion object):

__ https://github.com/spray/spray/blob/master/spray-can/src/main/scala/spray/can/server/HttpServer.scala

Bind
  Start listening for incoming connections on a particular port. The sender receives a ``Bound`` event upon completion.

Unbind
  Revert a previous ``Bind``. The sender receives an ``Unbound`` event upon completion.

GetStats
  Send the sender an ``HttpServer.Stats`` message containing simple server statistics.

ClearStats
  Reset the server statistics.


Request-Response Cycle
~~~~~~~~~~~~~~~~~~~~~~

After having bound to an interface/port the server spawns a new connection actor for every new connection.
As soon as a new request has been successfully read from the connection it is dispatched to the handler actor
provided as argument to the ``HttpServer`` constructor. The handler actor processes the request according
to the application logic and responds by sending an ``HttpResponse`` instance to the ``sender`` of the request.

The ``ActorRef`` used as the sender of an ``HttpRequest`` received by the handler is unique to the
request, i.e. several requests, even when coming in across the same connection, will appear to be sent from different
senders. *spray-can* uses these sender ``ActorRefs`` to coalesce the response with the request, so you cannot sent
several responses to the same sender. However, the different request parts of chunked requests arrive from the same
sender, and the different response parts of a chunked response need to be sent to the same sender as well.

.. caution:: Since the ``ActorRef`` used as the sender of a request is an :ref:`UnregisteredActorRef` it is not
 reachable remotely. This means that the actor designated as handler by the application needs to live in the same
 JVM as the ``HttpServer``.


Chunked Requests
~~~~~~~~~~~~~~~~

If the ``request-chunk-aggregation-limit`` config setting is set to zero the server also dispatches the individual
request parts of chunked requests to the handler actor. In these cases a full request consists of the following
messages:

- One ``ChunkedRequestStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The timer for checking request handling timeouts (if configured to non-zero) only starts running when the final
``ChunkedMessageEnd`` message was dispatched to the handler.


Chunked Responses
~~~~~~~~~~~~~~~~~

Alternatively to a single ``HttpResponse`` instance the handler can choose to respond to the request sender with the
following sequence of individual messages:

- One ``ChunkedResponseStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The timer for checking request handling timeouts (if configured to non-zero) will stop running as soon as the initial
``ChunkedResponseStart`` message has been received from the handler, i.e. there is currently no timeout checking
for and in between individual response chunks.


Request Timeouts
~~~~~~~~~~~~~~~~

If the handler does not respond to a request within the configured ``request-timeout`` period a
``spray.http.Timeout`` message is sent to the timeout handler, which can be the "regular" handler itself or
another actor (depending on the ``timeout-handler`` config setting). The timeout handler then has the chance to
complete the request within the time period configured as ``timeout-timeout``. Only if the timeout handler also misses
its deadline for completing the request will the ``HttpServer`` complete the request itself with a "hard-coded" error
response (which you can change by overriding the ``timeoutResponse`` method).

.. _HttpServer Send Confirmations:

Send Confirmations
~~~~~~~~~~~~~~~~~~

If required the server can reply with a "send confirmation" message to every response (part) coming in from the
handler. You request a send confirmation by modifying a response part with the ``withSentAck`` method. For example,
the following handler logic receives the String "ok" as an actor message after the response has been successfully
written to the connections socket:

.. includecode:: code/docs/HttpServerExamplesSpec.scala
   :snippet: example-2

Confirmation messages are especially helpful for triggering the sending of the next response part in a response
streaming scenario, since with such a design the application will never produce more data than the network can handle.

Send confirmations are always dispatched to the actor, which sent the respective response (part).


Closed Notifications
~~~~~~~~~~~~~~~~~~~~

When a connection is closed, for whatever reason, the server dispatches a ``Closed`` event message to the application.
Exactly which actor receives it depends on the current state of request processing.

The ``HttpServer`` sends ``Closed`` events coming in from the underlying :ref:`IOBridge` to

- the handler actor, if no request is currently open and the application doesn't use ``Per-Message`` handlers.
- the handler actor, if a request is currently open and no response part has yet been received.
- the sender of the last response part received by the server if the part is a ``ChunkedResponseStart`` or a
  ``MessageChunk``.
- the sender of the last response part received if a send confirmation was requested but not dispatched.

.. note:: The application can always choose to actively close a connection by sending a ``Close`` command to the sender
   of a request. However, during normal operation it is encouraged to make use of the ``Connection`` header to signal
   to the server whether the connection is to be closed after the response has been sent or not.


Connection Configuration
~~~~~~~~~~~~~~~~~~~~~~~~

After having received a request the applications request handler can send the following configuration messages to the
``sender`` in order to change config setting *for that connection only*:

SetIdleTimeout
  Change the connections ``idle-timeout``.

SetRequestTimeout
  Change the connections ``request-timeout``.

SetTimeoutTimeout
  Change the connections ``timeout-timeout``.

All these command messages are defined in the ``HttpServer`` companion object.


HTTP Headers
------------

The *spray-can* ``HttpServer`` always passes all received headers on to the application. Additionally the values of the
following request headers are interpreted by the server itself:

- ``Connection``
- ``Content-Length``
- ``Content-Type``
- ``Transfer-Encoding``
- ``Expect`` (the only supported expectation is "100-continue")
- ``Host`` (only the presence of this header is verified)

All other headers are of no interest to the server layer.

When sending out responses the server watches for a ``Connection`` header that your application might set and acts
accordingly, i.e. you can force the server to close the connection after having sent the response by including a
``Connection("close")`` header. To unconditionally force a connection keep-alive you can explicitly set a
``Connection("Keep-Alive")`` header. If you don't set an explicit ``Connection`` header the server will keep the
connection alive if the client supports this (i.e. it either sent a ``Connection: Keep-Alive`` header or advertised
HTTP/1.1 capabilities without sending a ``Connection: close`` header).

If your ``HttpResponse`` instances include any of the following headers they will be ignored and *not* rendered into
the response going out to the client (as the server sets these response headers itself):

- ``Content-Type``
- ``Content-Length``
- ``Transfer-Encoding``
- ``Date``
- ``Server``

.. note:: The ``Content-Type`` header has special status in *spray* since its value is part of the ``HttpEntity`` model
   class. Even though the header also remains in the ``headers`` list of the ``HttpRequest`` *sprays* higher layers
   (like *spray-routing*) only work with the Content-Type value contained in the ``HttpEntity``.


HTTP Pipelining
---------------

*spray-can* fully supports HTTP pipelining. If the configured ``pipelining-limit`` is greater than one the server will
accept several requests in a row (coming in across a single connection) and dispatch them to the application before the
first one has been responded to. This means that several requests will potentially be handled by the application at the
same time.

Since in many asynchronous applications request handling times can be somewhat undeterministic *spray-can* takes care of
properly ordering all responses coming in from your application before sending them out to "the wire".
I.e. your application will "see" requests in the order they are coming in but is *not* required to itself uphold this
order when generating responses.


SSL Support
-----------

If enabled via the ``ssl-encryption`` config setting the *spray-can* ``HttpServer`` requires all incoming connections to
be SSL/TLS encrypted. The constructor of the ``HttpServer`` actor takes an implicit argument of type
``ServerSSLEngineProvider``, which is essentially a function ``InetSocketAddress => SSLEngine``.
Whenever a new connection has been accepted the server uses the given function to create an ``javax.net.ssl.SSLEngine``
for the connection.

If you'd like to apply some custom configuration to your ``SSLEngine`` instances an easy way would be to bring a custom
engine provider into scope, e.g. like this::

    implicit val myEngineProvider = ServerSSLEngineProvider { engine =>
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      engine
    }

EngineProvider creation also relies on an implicitly available ``SSLContextProvider``, which is defined like this::

    trait SSLContextProvider {
      def createSSLContext: SSLContext
    }

The default ``SSLContextProvider`` simply provides an implicitly available "constant" ``SSLContext``, by default the
``SSLContext.getDefault`` is used. This means that the easiest way to have the server use a custom ``SSLContext``
is to simply bring one into scope implicitly::

    implicit val mySSLContext: SSLContext = {
      val context = SSLContext.getInstance("TLS")
      context.init(...)
      context
    }

