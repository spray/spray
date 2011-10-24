_spray-can_ is a low-overhead, high-performance, fully asynchronous HTTP 1.1 server and client library
implemented entirely in [Scala] on top of [Akka].

Both, the _spray-can_ server and the _spray-can_ client, sport the following features:
  
* Low per-connection overhead for supporting thousands of concurrent connections
* Efficient message parsing and processing logic for high throughput applications (> 50K requests/sec on ordinary consumer hardware)
* Full support for HTTP/1.1 persistant connections
* Full support for message pipelining
* Full support for asynchronous HTTP streaming (i.e. "chunked" transfer encoding)
* Akka-Actor and -Future based architecture for easy integration into your Akka applications
* No dependencies except for JavaSE 6, Scala 2.9 and [Akka] 1.2 (actors module).


## Basic Architecture & Design Philosophy

The _spray-can_ HttpServer is implemented as an [Akka] actor running on a single, private thread managing a Java
NIO selector. Incoming HTTP requests are dispatched as immutable messages to a service actor provided by the
application. Requests are completed by calling a responder function passed along (in continuation style) with the
request message.

_spray-can_ is scoped with a clear focus on the essential functionality of any HTTP 1.1 server:

* Connection management
* Message parsing and header separation
* Timeout management (for requests and connections)
* Response ordering (for transparent pipelining support)

All non-core features of typical HTTP servers (like request routing, file serving, compression, etc.) are left to the
next layer in the application stack, they are not implemented by _spray-can_ itself. Apart from general focus this design
keeps _spray-can_ small and light-weight as well as easy to understand and to maintain.
It also makes a _spray-can_ HttpServer a perfect "container" for a [spray-server] application, since _spray-can_ and
_spray-server_ nicely complement and interface into each other. The coming 0.8.0 release of _spray-server_ will
support _spray-can_ HttpServer out of the box.

(Everything in this section is also valid in analogy for the _spray-can_ HttpClient implementation.)


## Getting Started

The easiest way to get started with _spray-can_ is to try out the `server-example` and/or the `client-example` that's
part of the _spray-can_ codebase:

1. Git-clone this repository:

        $ git clone git://github.com/spray/spray-can.git my-project

2. Change directory into your clone:

        $ cd my-project

3. Launch [SBT][] (SBT 0.11.0) and run the server example:

        $ sbt "project server-example" run

4. Browse to <http://127.0.0.1:8080> and play around with the sample "app".

5. Run the client example:

        $ sbt "project client-example" run

6. Start hacking on the sources in

   * `server-example/src/main/scala/cc/spray/can/example/` and/or
   * `client-example/src/main/scala/cc/spray/can/example/`


For setting up your own project you need to pull in the _spray-can_ artifacts from the <http://scala-tools.org> repositories.
The latest release is `0.9.1`. It's built against Scala 2.9.1 and Akka 1.2.


## Server

### Basic Usage

The _spray-can_ HTTP server is really easy to use. All you need to do is start a new [HttpServer] actor as well as
an actor holding your custom request handling logic. Ideally these actors should be supervised:

    Supervisor(
      SupervisorConfig(
        OneForOneStrategy(List(classOf[Exception]), 3, 100),
        List(
          Supervise(Actor.actorOf(new MyService()), Permanent),
          Supervise(Actor.actorOf(new HttpServer()), Permanent)
        )
      )
    )

You can pass a [ServerConfig] instance to the [HttpServer] constructor. If you don't _spray-can_ looks for a server
configuration in your applications `akka.conf` file and uses default settings for anything not specified there.
By default your service actor needs to have the id `spray-root-service` in order to be found by the `HttpServer`.
After being started the server actor will accept new HTTP connections on the configured host interface (and port) and
dispatch all incoming HTTP requests as [RequestContext] messages to your service actor. You can take a look at the
server-examples [TestService] implementation for some example of what basic request handling with _spray-can_ might
look like.


### HTTP Headers

The _spray-can_ server always passes all received headers on to your application. Additionally the values of the
following request headers are interpreted by the server itself:

* `Connection`
* `Content-Length`
* `Transfer-Encoding`

All other headers are of no interest to the server layer.

When sending out responses the server watches for a `Connection` header that your application might set and acts
accordingly. I.e. you can force _spray-can_ to close the connection after having sent the response by including an
`HttpHeader("Connection", "close")`. To unconditionally force a connection keep-alive you can explicitly set a
`HttpHeader("Connection", "Keep-Alive")` header. If you don't set an explicit `Connection` header the server will keep
the connection alive if the client supports this (i.e. it either sent a "Connection: Keep-Alive" header or specified
HTTP/1.1 capabilities without sending a "Connection: close" header).

Your [HttpResponse] instances must not include explicit `Content-Length`, `Transfer-Encoding` or `Date` headers, since
_spray-can_ sets these automatically.


### Timeouts

If configured with a non-zero `requestTimeout` setting the _spray-can_ [HttpServer] will watch for request timeouts.
If your application logic does not complete a request by either calling `responder.complete` or
`responder.startChunkedResponse` on the incoming [RequestContext] within the configured timeout period the `HttpServer`
dispatches a [Timeout] message to the configured timeout actor (which may well be identical to your service actor).
The application then has another chance to complete the request, this time within the configured `timeoutTimeout`
period. Only if the request is still uncompleted after this time period the `HttpServer` will complete the request
itself with the result from its `timeoutTimeoutResponse` method (which you may override should the need arise).

If the [ServerConfig] has a non-zero `idleTimeout` the `HttpServer` will close idle connections after the respective
time period.


### Pipelining

HTTP pipelining is fully supported and completely transparent to your application. I.e. the client is allowed to send
a whole sequence of requests in a row without first waiting for responses. The _spray-can_ `HttpServer` dispatches
such pipelined requests to your service actor just as any other. However, since in many asynchronous applications
response times can be somewhat undeterministic _spray-can_ will take care of properly ordering all responses coming in
from your application before sending them out to "the wire". I.e. your application will "see" requests in the order
they are coming in but is not required to uphold this order when generating responses.


### Streaming / Chunked Messages

HTTP/1.1 defines the "chunked" transfer encoding for HTTP messages (requests and responses), which allows for the
sending of very large (even "infinite") HTTP requests or responses. Normally the client or server sending an
HTTP message needs to know the size of the message _before_ sending it. Chunked transfer encoding removes this
requirement, i.e. the client or server can start sending the message before the complete length is known (which might
be useful when transferring things like a live video or audio stream).

_spray-can_ fully supports chunked HTTP requests and responses in an asynchronous fashion.


#### Receiving Chunked Requests

When the _spray-can_ `HttpServer` receives the first bits of a chunked request from a client it starts a new Akka actor
for handling the different parts of the request. The [ServerConfig] contains a `streamActorCreator` member which
can hold a custom function performing the actual actor creation. _spray-can_ takes care of properly starting and
stopping the actor your custom function created as well as dispatching [MessageChunk] and [ChunkedRequestEnd] messages
to it. If you do not supply a custom `streamActorCreator` _spray-can_ uses a [BufferingRequestStreamActor] for incoming
chunked requests to transparently buffer and assemble regular [HttpResponse] instances before dispatching them to the
regular service actor.


#### Sending Chunked Responses

Your application can decide to respond to a request with a chunked response rather than a "traditional" one. This is
done via the `startChunkedResponse` method of the `responder` member of the incoming [RequestContext]. This method
returns a [ChunkedResponder] that allows for the sending of the individual message chunks as well as finalization of the
response.


### Shutting Down

The best way to shut down a _spray-can_ HTTP server instance is to send it an Akka `PoisonPill` message.
This will ensure the proper closing of all open connections as well as the freeing all other occupied resources.
Simply stopping the `HttpServer` actor by calling `stop()` (or `Actor.registry.shutdownAll()`) can sometimes lead to the
server thread not properly terminating.


## Client

The _spray-can_ `HttpClient` is the natural counterpart of the `HttpServer`. It shares all core features as well as the
basic "low-level" philosophy with the server.


### Basic Usage

Just like the `HttpServer` the `HttpClient` is implemented as an Akka actor running on a single, private thread.
So, in order to use it you first need to create and start it:

    Supervisor(
      SupervisorConfig(
        OneForOneStrategy(List(classOf[Exception]), 3, 100),
        List(Supervise(Actor.actorOf(new HttpClient()), Permanent))
      )
    )

You can pass a [ClientConfig] instance to the [HttpClient] constructor. If you don't _spray-can_ looks for a client
configuration in your applications `akka.conf` file and uses default settings for anything not specified there.
After being started the client actor will wait for [Connect] messages from your application, which it responds with
an object implementing the [HttpConnection] trait. Its scaladoc API documentation should give you a pretty good idea
of how to use an [HttpConnection] instance for sending requests and receive responses.

As you can see from this API the _spray-can_ `HttpClient` works on the basis of individual connections. There is no
higher-level support for automatic connection pooling and such, since this is considered the responsibility of the
next-higher application layer.


### Timeouts

If configured with a non-zero `requestTimeout` setting the _spray-can_ [HttpClient] will watch for request timeouts.
If the server does not respond within the configured timeout period a respective [HttpClientException] instance will
be created and delivered to either the receiver actor or the response future.
Additionally the `HttpClient` will automatically close idle connections if the configured `idleTimeout` is non-zero.


### Pipelining

If you know that the HTTP server your application connects to supports request pipelining you can send several requests
in a row without first waiting for responses to come in. The **HttpDialog DSL** (see below) might make working with
persistant connections and request pipelinging a bit easier.


### Streaming / Chunked Messages

Just like the `HttpServer` the `HttpClient` supports sending chunked requests as well as receiving chunked responses
in an asynchronous fashion. The scaladoc API documentation of the [HttpConnection] trait should be rather
self-explanatory with regard to its usage.


### HttpDialog DSL

As a thin layer on top of the `HttpClient` _spray-can_ provides a convenience mini-DSL that makes working with
HTTP connections a bit easier. It is probably best explained by example.

The following snippet shows a minimal, single-request [HttpDialog]:

    import HttpClient._
    val response: Future[HttpResponse] =
          HttpDialog("github.com")
          .send(HttpRequest(method = GET, uri = "/"))
          .end

A non-pipelined two-request dialog:

    val responses: Future[Seq[HttpResponse]] =
          HttpDialog("example.com")
          .send(HttpRequest(POST, "/shout").withBody("yeah!"))
          .awaitResponse
          .send(HttpRequest(PUT, "/count").withBody("42"))
          .end

A pipelined three-request dialog:

    val responses: Future[Seq[HttpResponse]] =
          HttpDialog(host = "img.example.com", port = 8888)
          .send(HttpRequest(GET, "a.gif"))
          .send(HttpRequest(GET, "b.gif"))
          .send(HttpRequest(GET, "c.gif"))
          .end

A request -> response -> request dialog:

    val response: Future[HttpResponse] =
          HttpDialog("example.com")
          .send(HttpRequest(GET, "/ping"))
          .reply(response => HttpRequest(GET, "/ping2", body = response.body))
          .end

Note that the explicit result type annotations are only shown here for documentation. They can be inferred and are
therefore not required.


### Shutting Down

The best way to shut down a _spray-can_ HTTP client instance is to send it an Akka `PoisonPill` message.
This will ensure the proper closing of all open connections as well as the freeing all other occupied resources.
Simply stopping the `HttpClient` actor by calling `stop()` (or `Actor.registry.shutdownAll()`) can sometimes lead to
the client thread not properly terminating.


## Support

Many questions might already be answered by the _spray-can_ [API documentation].

You can also turn to the active <http://groups.google.com/group/spray-user> mailing list for support.


## License

_spray-can_ is licensed under [APL 2.0].


## Patch Policy

Feedback and contributions to the project, no matter what kind, are always very welcome.
However, patches can only be accepted from their original author.
Along with any patches, please state that the patch is your original work and that you license the work to the
_spray-can_ project under the project’s open source license.

  [Scala]: http://www.scala-lang.org/
  [Akka]: http://akka.io
  [spray-server]: http://spray.cc
  [SBT]: http://code.google.com/p/simple-build-tool/wiki/DocumentationHome
  [HttpServer]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.HttpServer
  [ServerConfig]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.ServerConfig
  [RequestContext]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.RequestContext
  [TestService]: https://github.com/spray/spray-can/blob/master/server-example/src/main/scala/cc/spray/can/example/TestService.scala
  [HttpResponse]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.HttpResponse
  [Timeout]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.Timeout
  [MessageChunk]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.MessageChunk
  [ChunkedRequestEnd]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.ChunkedRequestEnd
  [HttpClientException]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.HttpClientException
  [BufferingRequestStreamActor]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.BufferingRequestStreamActor
  [ChunkedResponder]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.ChunkedResponder
  [ClientConfig]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.ClientConfig
  [HttpClient]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.HttpClient
  [Connect]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.Connect
  [HttpConnection]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.HttpConnection
  [HttpDialog]: http://spray.github.com/spray/api/spray-can/index.html#cc.spray.can.HighLevelHttpClient$HttpDialog
  [API documentation]: http://spray.github.com/spray/api/spray-can/index.html
  [APL 2.0]: http://www.apache.org/licenses/LICENSE-2.0
