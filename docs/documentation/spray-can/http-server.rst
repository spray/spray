.. _HTTP Server:

HTTP Server
===========

The *spray-can* HTTP server is an embedded, actor-based, fully asynchronous, low-level, low-overhead and
high-performance HTTP/1.1 server implemented on top of `Akka IO`_ / :ref:`spray-io`.

It sports the following features:

- Low per-connection overhead for supporting many thousand concurrent connections
- Efficient message parsing and processing logic for high throughput applications
- Full support for `HTTP persistent connections`_
- Full support for `HTTP pipelining`_
- Full support for asynchronous HTTP streaming (i.e. "chunked" transfer encoding)
- Optional SSL/TLS encryption
- Actor-based architecture and API for easy integration into your Akka applications

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
the next-higher layer in the application stack, they are not implemented by *spray-can* itself.
Apart from general focus this design keeps the server small and light-weight as well as easy to understand and
maintain. It also makes a *spray-can* HTTP server a perfect "container" for a :ref:`spray-routing` application,
since *spray-can* and *spray-routing* nicely complement and interface into each other.


Basic Architecture
------------------

The *spray-can* HTTP server is implemented by two types of Akka actors, which sit on top of `Akka IO`_. When you tell
*spray-can* to start a new server instance on a given port an ``HttpListener`` actor is started, which accepts incoming
connections and for each one spawns a new ``HttpServerConnection`` actor, which then manages the connection for the
rest of its lifetime.
These connection actors process the requests coming in across their connection and dispatch them as immutable
:ref:`spray-http` ``HttpRequest`` instances to a "handler" actor provided by your application.
The handler can complete a request by simply replying with an ``HttpResponse`` instance:

.. includecode:: code/docs/HttpServerExamplesSpec.scala
   :snippet: simple-reply

Your code never deals with the ``HttpListener`` and ``HttpServerConnection`` actor classes directly, in fact they are
marked ``private`` to the *spray-can* package. All communication with these actors happens purely via actor messages,
the majority of which are defined in the `spray.can.Http`_ object.

.. _Akka IO: http://doc.akka.io/docs/akka/2.2.0-RC1/scala/io.html
.. _spray.can.Http: https://github.com/spray/spray/blob/release/1.2/spray-can/src/main/scala/spray/can/Http.scala#L31


Starting
--------

A *spray-can* HTTP server is started by sending an ``Http.Bind`` command to the ``Http`` extension:

.. includecode:: code/docs/HttpServerExamplesSpec.scala
   :snippet: bind-example

With the ``Http.Bind`` command you register an application-level "listener" actor and specify the interface and port to
bind to. Additionally the ``Http.Bind`` command also allows you to define socket options as well as a larger number of
settings for configuring the server according to your needs.

The sender of the ``Http.Bind`` command (e.g. an actor you have written) will receive an ``Http.Bound`` reply after
the HTTP layer has successfully started the server at the respective endpoint. In case the bind fails (e.g. because
the port is already busy) an ``Http.CommandFailed`` message is dispatched instead.

The sender of the ``Http.Bound`` confirmation event is *spray-can*'s ``HttpListener`` instance. You will need this
``ActorRef`` if you want to stop the server later.


Stopping
--------

To explicitly stop the server, send an ``Http.Unbind`` command to the ``HttpListener`` instance (the ``ActorRef``
for this instance is available as the sender of the ``Http.Bound`` confirmation event from when the server
was started).

The listener will reply with an ``Http.Unbound`` event after successfully unbinding from the port (or with
an ``Http.CommandFailed`` in the case of error). At that point no further requests will be accepted by the
server.

Any requests which were in progress at the time will proceed to completion. When the last request has terminated,
the ``HttpListener`` instance will exit. You can monitor for this (e.g. so that you can shutdown the ``ActorSystem``)
by watching the listener actor and awaiting a ``Terminated`` message.


Message Protocol
----------------

After having successfully bound an ``HttpListener`` your application communicates with the *spray-can*-level connection
actors via a number of actor messages that are explained in this section.


Request-Response Cycle
~~~~~~~~~~~~~~~~~~~~~~

When a new connection has been accepted the application-level listener, which was registered with the ``Http.Bind``
command, receives an ``Http.Connected`` event message from the connection actor. The application must reply to it with
an ``Http.Register`` command within the configured ``registration-timeout`` period, otherwise the connection will be
closed.

With the ``Http.Register`` command the application tells the connection actor which actor should handle incoming
requests. The application is free to register the same actor for all connections (a "singleton handler"), a new one for
every connection ("per-connection handlers") or anything in between. After the connection actor has received the
``Http.Register`` command it starts reading requests from the connection and dispatches them as
``spray.http.HttpRequestPart`` messages to the handler. The handler actor should then process the request according to
the application logic and respond by sending an ``HttpResponsePart`` instance to the ``sender`` of the request.

The ``ActorRef`` used as the sender of an ``HttpRequestPart`` received by the handler is unique to the request, i.e.
several requests, even when coming in across the same connection, will appear to be sent from different senders.
*spray-can* uses this sender ``ActorRef`` to coalesce the response with the request, so you cannot send several
responses to the same sender. However, the different request parts of chunked requests arrive from the same sender,
and the different response parts of a chunked response need to be sent to the same sender as well.

.. caution:: Since the ``ActorRef`` used as the sender of a request is an UnregisteredActorRef_ it is not
   reachable remotely. This means that the actor designated as handler by the application needs to live in the same
   JVM as the HTTP extension.

.. _UnregisteredActorRef: /documentation/1.1-M7/spray-util/#unregisteredactorref

Chunked Requests
~~~~~~~~~~~~~~~~

If the ``request-chunk-aggregation-limit`` config setting is set to zero the connection actor also dispatches the
individual request parts of chunked requests to the handler actor. In these cases a full request consists of the
following messages:

- One ``ChunkedRequestStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The timer for checking request handling timeouts (if not configured to ``infinite``) only starts running when the final
``ChunkedMessageEnd`` message was dispatched to the handler.


Chunked Responses
~~~~~~~~~~~~~~~~~

Alternatively to a single ``HttpResponse`` instance the handler can choose to respond to the request sender with the
following sequence of individual messages:

- One ``ChunkedResponseStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The timer for checking request handling timeouts (if not configured to ``infinite``) will stop running as soon as the
initial ``ChunkedResponseStart`` message has been received from the handler, i.e. there is currently no timeout checking
for and in between individual response chunks.


Request Timeouts
~~~~~~~~~~~~~~~~

If the handler does not respond to a request within the configured ``request-timeout`` period a
``spray.http.Timedout`` message is sent to the timeout handler, which can be the "regular" handler itself or
another actor (depending on the ``timeout-handler`` config setting). The timeout handler then has the chance to
complete the request within the time period configured as ``timeout-timeout``. Only if the timeout handler also misses
its deadline for completing the request will the connection actor complete the request itself with a "hard-coded" error
response.

In order to change the respective config setting *for that connection only* the application can send the following
messages to the ``sender`` of a request (part) or the connection actor:

- spray.io.ConnectionTimeouts.SetIdleTimeout
- spray.http.SetRequestTimeout
- spray.http.SetTimeoutTimeout


Closed Notifications
~~~~~~~~~~~~~~~~~~~~

When a connection is closed, for whatever reason, the connection actor dispatches one of five defined
``Http.ConnectionClosed`` event message to the application (see the :ref:`CommonBehavior` chapter for more info).

Exactly which actor receives it depends on the current state of request processing.
The connection actor sends ``Http.ConnectionClosed`` events coming in from the underlying IO layer

- to the handler actor
- to the *request* chunk handler if one is defined and no response part was yet received
- to the sender of the last received response part

  - if the ACK for an ACKed response part has not yet been dispatched
  - if a *response* chunk stream has not yet been finished (with a ``ChunkedMessageEnd``)

.. note:: The application can always choose to actively close a connection by sending one of the three defined
   ``Http.CloseCommand`` messages to the sender of a request or the connection actor (see :ref:`CommonBehavior`).
   However, during normal operation it is encouraged to make use of the ``Connection`` header to signal to the
   connection actor whether or not the connection is to be closed after the response has been sent.


Server Statistics
~~~~~~~~~~~~~~~~~

If the ``stats-support`` config setting is enabled the server will continuously count connections, requests, timeouts
and other basic statistics. You can ask the ``HttpListener`` actor (i.e. the sender ``ActorRef`` of the ``Http.Bound``
event message!) to reply with an instance of the ``spray.can.server.Stats`` class by sending it an ``Http.GetStats``
command. This is what you will get back:

.. includecode:: /../spray-can/src/main/scala/spray/can/server/StatsSupport.scala
   :snippet: Stats

By sending the listener an ``Http.ClearStats`` command message you can trigger a reset of the stats.


HTTP Headers
------------

When a *spray-can* connection actor receives an HTTP request it tries to parse all its headers into their respective
*spray-http* model classes. No matter whether this succeeds or not, the connection actor will always pass on all
received headers to the application. Unknown headers as well as ones with invalid syntax (according to *spray*'s header
parser) will be made available as ``RawHeader`` instances. For the ones exhibiting parsing errors a warning message is
logged depending on the value of the ``illegal-header-warnings`` config setting.

When sending out responses the connection actor watches for a ``Connection`` header set by the application and acts
accordingly, i.e. you can force the connection actor to close the connection after having sent the response by including
a ``Connection("close")`` header. To unconditionally force a connection keep-alive you can explicitly set a
``Connection("Keep-Alive")`` header. If you don't set an explicit ``Connection`` header the connection actor will keep
the connection alive if the client supports this (i.e. it either sent a ``Connection: Keep-Alive`` header or advertised
HTTP/1.1 capabilities without sending a ``Connection: close`` header).

The following response headers are managed by the *spray-can* layer itself and as such are **ignored** if you "manually"
add them to the response (you'll see a warning in your logs):

- ``Content-Type``
- ``Content-Length``
- ``Transfer-Encoding``
- ``Date``
- ``Server``

There are three exceptions:

1. Responses to HEAD requests that have an empty entity are allowed to contain a user-specified ``Content-Type`` header.
2. Responses in ``ChunkedResponseStart`` messages that have an empty entity are allowed to contain a user-specified
   ``Content-Type`` header.
3. Responses in ``ChunkedResponseStart`` messages are allowed to contain a user-specified
   ``Content-Length`` header if ``spray.can.server.chunkless-streaming`` is enabled.

.. note:: The ``Content-Type`` header has special status in *spray* since its value is part of the ``HttpEntity`` model
   class. Even though the header also remains in the ``headers`` list of the ``HttpRequest`` *sprays* higher layers
   (like *spray-routing*) only work with the ``ContentType`` value contained in the ``HttpEntity``.


HTTP Pipelining
---------------

*spray-can* fully supports HTTP pipelining. If the configured ``pipelining-limit`` is greater than one a connection
actor will accept several requests in a row (coming in across a single connection) and dispatch them to the application
even before the first one has been responded to. This means that several requests will potentially be handled by the
application at the same time.

Since in many asynchronous applications request handling times can be somewhat undeterministic *spray-can* takes care of
properly ordering all responses coming in from your application before sending them out to "the wire".
I.e. your application will "see" requests in the order they are coming in but is *not* required to itself uphold this
order when generating responses.


SSL Support
-----------

If enabled via the ``ssl-encryption`` config setting the *spray-can* connection actors pipe all IO traffic through an
``SslTlsSupport`` module, which can perform transparent SSL/TLS encryption. This module is configured via the implicit
``ServerSSLEngineProvider`` member on the ``Http.Bind`` command message. An ``ServerSSLEngineProvider`` is essentially
a function ``PipelineContext ⇒ Option[SSLEngine]``, which determines whether encryption is to be performed and, if so,
which ``javax.net.ssl.SSLEngine`` instance is to be used.

If you'd like to apply some custom configuration to your ``SSLEngine`` instances an easy way would be to bring a custom
engine provider into scope, e.g. like this:

.. includecode:: code/docs/HttpServerExamplesSpec.scala
   :snippet: sslengine-config

EngineProvider creation also relies on an implicitly available ``SSLContextProvider``, which is defined like this:

.. includecode:: /../spray-io/src/main/scala/spray/io/SslTlsSupport.scala
   :snippet: source-quote-SSLContextProvider

The default ``SSLContextProvider`` simply provides an implicitly available "constant" ``SSLContext``, by default the
``SSLContext.getDefault`` is used. This means that the easiest way to have the server use a custom ``SSLContext``
is to simply bring one into scope implicitly:

.. includecode:: code/docs/HttpServerExamplesSpec.scala
   :snippet: sslcontext-provision
