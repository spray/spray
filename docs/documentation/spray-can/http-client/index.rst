.. _HTTP Client APIs:

HTTP Client APIs
================

Apart from the server-side HTTP abstractions *spray-can* also contains a client-side HTTP implementation that enables
your application to interact with other HTTP servers. And just like on the server side it is actor-based,
fully asynchronous, low-overhead and built on top of `Akka IO`_ / :ref:`spray-io`.

As the counterpart of the :ref:`HTTP Server` it shares all core features as well as the basic "low-level" philosophy
with the server-side constructs.

The *spray-can* client API offers three different levels of abstraction that you can work with
(from lowest to highest level):

.. toctree::
   :maxdepth: 1

   connection-level
   host-level
   request-level

.. _Akka IO: http://doc.akka.io/docs/akka/2.2.0-RC1/scala/io.html


Basic API Structure
-------------------

Depending on the specific needs of your use case you should pick the

:ref:`ConnectionLevelApi`
  for full-control over when HTTP connections are opened/closed and how requests are scheduled across them.

:ref:`HostLevelApi`
  for letting *spray-can* manage a connection-pool for *one specific* host.

:ref:`RequestLevelApi`
  for letting *spray-can* take over all connection management.

You can interact with *spray-can* on different levels at the same time and, independently of which API level you choose,
*spray-can* will happily handle many thousand concurrent connections to a single or many different hosts.


Chunked Requests
----------------

While the host- and request-level APIs do not currently support chunked (streaming) HTTP requests the connection-level
API does. Alternatively to a single ``HttpRequest`` the application can choose to send this sequence of individual
messages:

- One ``ChunkedRequestStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The connection actor will render these as one logical HTTP request with ``Transfer-Encoding: chunked``.
The timer for checking request timeouts (if configured to non-zero) only starts running when the final
``ChunkedMessageEnd`` message was sent out.


Chunked Responses
-----------------

Chunked (streaming) responses are supported by all three API levels. If the ``response-chunk-aggregation-limit``
connection config setting is set to zero the individual response parts of chunked requests are dispatched to the
application as they come in. In these cases a full response consists of the following messages:

- One ``ChunkedResponseStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The timer for checking request timeouts (if configured to non-zero) will stop running as soon as the initial
``ChunkedResponseStart`` message has been received, i.e. there is currently no timeout checking
for and in between individual response chunks.


HTTP Headers
------------

When a *spray-can* connection actor receives an HTTP response it tries to parse all its headers into their respective
*spray-http* model classes. No matter whether this succeeds or not, the connection actor will always pass on all
received headers to the application. Unknown headers as well as ones with invalid syntax (according to *spray*'s header
parser) will be made available as ``RawHeader`` instances. For the ones exhibiting parsing errors a warning message is
logged depending on the value of the ``illegal-header-warnings`` config setting.

The following message headers are managed by the *spray-can* layer itself and as such are **ignored** if you "manually"
add them to an outgoing request:

- ``Content-Type``
- ``Content-Length``
- ``Transfer-Encoding``

There is one exception: for requests with an empty entity *spray-can* will render manually added ``Content-Type``
headers.

Additionally *spray-can* will render a

- ``Host`` request header if none is explicitly added.
- ``User-Agent`` header if one is configured. If none is configured an explicitly added added ``User-Agent`` header is
  also rendered.

.. note:: The ``Content-Type`` header has special status in *spray* since its value is part of the ``HttpEntity`` model
   class. Even though the header also remains in the ``headers`` list of the ``HttpResponse`` *sprays* higher layers
   (like *spray-client*) only work with the ``ContentType`` value contained in the ``HttpEntity``.


SSL Support
-----------

SSL support is enabled

 - for the connection-level API by setting ``Http.Connect(sslEncryption = true)`` when connecting to a server
 - for the host-level API by setting ``Http.HostConnectorSetup(sslEncryption = true)`` when creating a host connector
 - for the request-level API by using an ``https`` URL in the request

Particular SSL settings can be configured via the implicit
``ClientSSLEngineProvider`` member on the ``Http.Connect`` and ``Http.HostConnectorSetup`` command messages.
An ``ClientSSLEngineProvider`` is essentially a function ``PipelineContext ⇒ Option[SSLEngine]`` which determines
whether encryption is to be performed and, if so, which ``javax.net.ssl.SSLEngine`` instance is to be used. By returning
``None`` the ``ClientSSLEngineProvider`` can decide to disable SSL support even if SSL support was requested by the means
described above.

If you'd like to apply some custom configuration to your ``SSLEngine`` instances an easy way would be to bring a custom
engine provider into scope, e.g. like this:

.. includecode:: ../code/docs/HttpClientExamplesSpec.scala
   :snippet: sslengine-config

EngineProvider creation also relies on an implicitly available ``SSLContextProvider``, which is defined like this:

.. includecode:: /../spray-io/src/main/scala/spray/io/SslTlsSupport.scala
   :snippet: source-quote-SSLContextProvider

The default ``SSLContextProvider`` simply provides an implicitly available "constant" ``SSLContext``, by default the
``SSLContext.getDefault`` is used. This means that the easiest way to have the server use a custom ``SSLContext``
is to simply bring one into scope implicitly:

.. includecode:: ../code/docs/HttpServerExamplesSpec.scala
   :snippet: sslcontext-provision

