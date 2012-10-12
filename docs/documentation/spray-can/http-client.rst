.. _HttpClient:

HttpClient
==========

The *spray-can* ``HttpClient`` is a low-level, low-overhead, high-performance, fully asynchronous,
non-blocking and actor-based HTTP/1.1 client implemented on top of :ref:`spray-io`.

It is the counterpart of the :ref:`HttpServer` and shares all core features as well as the basic
"low-level" philosophy with the server.


Basic Architecture
------------------

The *spray-can* ``HttpClient`` is implemented as an Akka actor, which talks to an underlying :ref:`IOBridge` and spawns
new child actors for every new connection. These connection actors render the outgoing requests, process the incoming
responses and dispatch them back to the application.

One ``HttpClient`` instance can handle many thousand concurrent requests, so normally there is no reason to instantiate
more than one ``HttpClient`` per application. However, since its overhead is small it's not a problem if you do.
(One reason might be that you need several clients with different configurations.)


Starting and Stopping
---------------------

Since the ``HttpClient`` is a regular actor it is started and stopped like any other one.
The :ref:`simple-http-client` example contains a complete and working code example.


Message Protocol
----------------

After having started an ``HttpClient`` you typically send it a ``Connect`` command message (all commands are defined
in the HttpClient__ companion object). The client will attempt to connect to the target server and either respond with
an ``HttpClient.Connected`` event after the connection has been established, or a ``Status.Failure`` message
(which is automatically turned into Future failures if the ``Connect`` was sent with an ``ask``).

__ https://github.com/spray/spray/blob/master/spray-can/src/main/scala/spray/can/client/HttpClient.scala

After the connection has been established the application then sends an ``HttpRequest`` to the sender of the
``Connected`` message (which is the connection actor responsible for the connection). After having received and parsed
the response the connection actor then replies with the ``HttpResponse`` instance to the sender of the request
(which might be a Future if the ``HttpRequest`` was sent with an ``ask``).

The application can then decide to send another request across the same connection (reusing the connection actor) or
close the connection by sending ``Close`` command to the connection actor.


Chunked Requests
~~~~~~~~~~~~~~~~

Alternatively to a single ``HttpRequest`` the application can choose to send the sequence of individual messages:

- One ``ChunkedRequestStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The ``HttpClient`` will render these as one logical HTTP request with the ``chunked`` transfer-encoding.
The timer for checking request timeouts (if configured to non-zero) only starts running when the final
``ChunkedMessageEnd`` message was sent out.


Chunked Responses
~~~~~~~~~~~~~~~~~

If the ``response-chunk-aggregation-limit`` config setting is set to zero the client also dispatches the individual
response parts of chunked requests back to the application. In these cases a full response consists of the following
messages:

- One ``ChunkedResponseStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The timer for checking request timeouts (if configured to non-zero) will stop running as soon as the initial
``ChunkedResponseStart`` message has been received, i.e. there is currently no timeout checking
for and in between individual response chunks.


Request Timeouts
~~~~~~~~~~~~~~~~

If no response to a request is received within the configured ``request-timeout`` period the ``HttpClient`` closes
the connection, upon which the application receives a ``Closed(RequestTimeout)`` event message.


Send Confirmations
~~~~~~~~~~~~~~~~~~

If required the client can reply with a "send confirmation" message to every request (part) received by the application.
You request a send confirmation by modifying a request part with the ``withSentAck`` method (see the server-side
:ref:`HttpServer Send Confirmations` section for example code).
Confirmation messages are especially helpful for triggering the sending of the next request part in a request
streaming scenario, since with such a design the application will never produce more data than the network can handle.

Send confirmations are always dispatched to the actor, which sent the respective request (part).


Closed Notifications
~~~~~~~~~~~~~~~~~~~~

When a connection is closed, for whatever reason, the ``HttpClient`` dispatches a ``Closed`` event message to the
application. This message carries a ``reason`` member whose possible values are define here__.

__ https://github.com/spray/spray/blob/master/spray-io/src/main/scala/spray/io/ConnectionClosedReason.scala


Connection Configuration
~~~~~~~~~~~~~~~~~~~~~~~~

After having received the ``Connected`` message the applications can send the following configuration messages to the
``sender`` (i.e. the connection actor) in order to change config setting *for that connection only*:

SetIdleTimeout
  Change the connections ``idle-timeout``.

SetRequestTimeout
  Change the connections ``request-timeout``.

All these command messages are defined in the ``HttpClient`` companion object.


HTTP Headers
------------

The *spray-can* ``HttpClient`` always passes all received headers back to your application. Additionally the values of
the following request headers are interpreted by the client itself:

- ``Content-Length``
- ``Content-Type``
- ``Transfer-Encoding``

All other headers are of no interest to the server layer.

If your ``HttpRequest`` instances include any of the following headers they will be ignored and *not* rendered into
the request going out to the client (as the client sets these request headers itself):

- ``Content-Type``
- ``Content-Length``
- ``Transfer-Encoding``
- ``Host``
- ``User-Agent``

.. note:: The ``Content-Type`` header has special status in *spray* since its value is part of the ``HttpEntity`` model
   class. Even though the header also remains in the ``headers`` list of the ``HttpResponse`` *sprays* higher layers
   (like *spray-client*) only work with the Content-Type value contained in the ``HttpEntity``.


SSL Support
-----------

If enabled via the ``ssl-encryption`` config setting the *spray-can* ``HttpClient`` allows outgoing connections to be
SSL/TLS encrypted. This is signalled on a per-connection basis by setting the ``tag`` member of the ``Connect`` command
to ``HttpClient.SslEnabled``.

.. note:: SSL encryption is only generally available for the ``HttpClient`` if the ``ssl-encryption`` config setting is
   enabled. Using the ``SslEnabled`` tag on ``Connect`` command when ``ssl-encryption`` is off in the settings has no
   effect.

The constructor of the ``HttpClient`` actor takes an implicit argument of type ``ClientSSLEngineProvider``, which is
essentially a function ``InetSocketAddress => SSLEngine``. Whenever a new connection has been accepted the client uses
the given function to create an ``javax.net.ssl.SSLEngine`` for the connection.

If you'd like to apply some custom configuration to your ``SSLEngine`` instances an easy way would be to bring a custom
engine provider into scope, e.g. like this::

    implicit val myEngineProvider = ClientSSLEngineProvider { engine =>
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
