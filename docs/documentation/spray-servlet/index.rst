.. _spray-servlet:

spray-servlet
=============

*spray-servlet* is an adapter layer providing (a subset of) the *spray-can* :ref:`HTTP Server` interface on top of the
Servlet API. As one main application it enables the use of :ref:`spray-routing` in a servlet container.


Dependencies
------------

Apart from the Scala library (see :ref:`Current Versions` chapter) *spray-can* depends on

- :ref:`spray-http`
- :ref:`spray-util`
- :ref:`spray-io` (only required until the upgrade to Akka 2.2, will go away afterwards)
- akka-actor 2.1.x (with 'provided' scope, i.e. you need to pull it in yourself)
- the Servlet-3.0 API (with 'provided' scope, usually automatically available from your servlet container)


Installation
------------

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-servlet* into your classpath.


Configuration
-------------

Just like Akka *spray-servlet* relies on the `typesafe config`_ library for configuration. As such its JAR contains a
``reference.conf`` file holding the default values of all configuration settings. In your application you typically
provide an ``application.conf``, in which you override Akka and/or *spray* settings according to your needs.

.. note:: Since *spray* uses the same configuration technique as Akka you might want to check out the
   `Akka Documentation on Configuration`_.

.. _typesafe config: https://github.com/typesafehub/config
.. _Akka Documentation on Configuration: http://doc.akka.io/docs/akka/2.0.4/general/configuration.html

This is the ``reference.conf`` of the *spray-servlet* module:

.. literalinclude:: /../spray-servlet/src/main/resources/reference.conf
   :language: bash


Basic Architecture
------------------

The central element of *spray-servlet* is the ``Servlet30ConnectorServlet``. Its job is to accept incoming HTTP
requests, suspend them (using Servlet 3.0 ``startAsync``), create immutable :ref:`spray-http` ``HttpRequest`` instances
for them and dispatch these to a service actor provided by the application.

The messaging API as seen from the application is modeled as closely as possible like its counterpart, the
*spray-can* :ref:`HTTP Server`.

In the most basic case, the service actor completes a request by simply replying
with an ``HttpResponse`` instance to the request sender::

    def receive = {
      case HttpRequest(...) => sender ! HttpResponse(...)
    }


Starting and Stopping
---------------------

A *spray-servlet* application is started by the servlet container. The application JAR should contain a ``web.xml``
similar to `this one`__ from the `simple-spray-servlet-server`_ example.

__ https://github.com/spray/spray/blob/master/examples/spray-servlet/simple-spray-servlet-server/src/main/webapp/WEB-INF/web.xml

The ``web.xml`` registers a ``ServletContextListener`` (``spray.servlet.Initializer``), which initializes the
application when the servlet is started. The ``Initializer`` loads the configured ``boot-class`` and instantiates it
using the default constructor, which must be available. The boot class must implement the ``WebBoot`` trait, which is
defined like this:

.. includecode:: /../spray-servlet/src/main/scala/spray/servlet/WebBoot.scala
   :snippet: source-quote

A very basic boot class implementation is `this one`__ from the `simple-spray-servlet-server`_ example.

__ https://github.com/spray/spray/blob/master/examples/spray-servlet/simple-spray-servlet-server/src/main/scala/spray/examples/Boot.scala

The boot class is responsible for creating the Akka ``ActorSystem`` for the application as well as the service actor.
When the application is shut down by the servlet container the ``Initializer`` shuts down the ``ActorSystem``, which
cleanly terminates all application actors including the service actor.


Message Protocol
----------------

Just like in its counterpart, the *spray-can* :ref:`HTTP Server`, all communication between the connector servlet and
the application happens through actor messages.


Request-Response Cycle
~~~~~~~~~~~~~~~~~~~~~~

As soon as a new request has been successfully read from the servlet API it is dispatched to the service actor
created by the boot class. The service actor processes the request according
to the application logic and responds by sending an ``HttpResponse`` instance to the ``sender`` of the request.

The ``ActorRef`` used as the sender of an ``HttpRequest`` received by the service actor is unique to the
request, i.e. each request will appear to be sent from different senders. *spray-servlet* uses these sender
``ActorRefs`` to coalesce the response with the request, so you cannot sent several responses to the same sender.
However, the different response parts of a chunked response need to be sent to the same sender.

.. caution:: Since the ``ActorRef`` used as the sender of a request is an UnregisteredActorRef_ it is not
   reachable remotely. This means that the service actor needs to live in the same JVM as the connector servlet.
   This will be changed before the 1.1 final release.

.. _UnregisteredActorRef: /documentation/1.1-M7/spray-util/#unregisteredactorref


Chunked Responses
~~~~~~~~~~~~~~~~~

Alternatively to a single ``HttpResponse`` instance the handler can choose to respond to the request sender with the
following sequence of individual messages:

- One ``ChunkedResponseStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The connector servlet writes the individual response parts into the servlet response ``OutputStream`` and flushes it.
Whether these parts are really rendered "to the wire" as chunked message parts depends on the servlet container
implementation. The Servlet API has not dedicated support for chunked responses.


Request Timeouts
~~~~~~~~~~~~~~~~

If the service actor does not complete a request within the configured ``request-timeout`` period a
``spray.http.Timedout`` message is sent to the timeout handler, which can be the service actor itself or
another actor (depending on the ``timeout-handler`` config setting). The timeout handler then has the chance to
complete the request within the time period configured as ``timeout-timeout``. Only if the timeout handler also misses
its deadline for completing the request will the connector servlet complete the request itself with a "hard-coded"
error response (which you can change by overriding the ``timeoutResponse`` method of the ``Servlet30ConnectorServlet``).


Send Confirmations
~~~~~~~~~~~~~~~~~~

If required the connector servlet can reply with a "send confirmation" message to every response (part) coming in from
the application. You request a send confirmation by modifying a response part with the ``withAck`` method
(see the :ref:`ACKed Sends` section of the *spray-can* documentation for example code).
Confirmation messages are especially helpful for triggering the sending of the next response part in a response
streaming scenario, since with such a design the application will never produce more data than the servlet container can
handle.

Send confirmations are always dispatched to the actor, which sent the respective response (part).


Closed Notifications
~~~~~~~~~~~~~~~~~~~~

The Servlet API completely hides the actual management of the HTTP connections from the application. Therefore the
connector servlet has no real way of finding out whether a connection was closed or not. However, if the connection
was closed unexpectedly for whatever reason a subsequent attempt to write to it usually fails with an ``IOException``.
In order to adhere to same message protocol as the *spray-can* :ref:`HTTP Server` the connector servlet therefore
dispatches any exception, which the servlet container throws when a response (part) is written, back to the application
wrapped in an ``Tcp.ErrorClosed`` message.

In addition the connector servlet also dispatches ``Tcp.Closed`` notification messages after the final part of a
response has been successfully written to the servlet container. This allows the application to use the same execution
model for *spray-servlet* as it would for the *spray-can* :ref:`HTTP Server`.


HTTP Headers
------------

The connector servlet always passes all received headers on to the application. Additionally the values of the
``Content-Length`` and ``Content-Type`` headers are interpreted by the servlet itself. All other headers are of no
interest to it.

Also, if your ``HttpResponse`` instances include a ``Content-Length`` or ``Content-Type`` header they will be ignored
and *not* written through to the servlet container (as the connector servlet sets these response headers itself).

.. note:: The ``Content-Type`` header has special status in *spray* since its value is part of the ``HttpEntity`` model
   class. Even though the header also remains in the ``headers`` list of the ``HttpRequest`` *spray's* higher layers
   (like *spray-routing*) only work with the Content-Type value contained in the ``HttpEntity``.


Differences to spray-can
------------------------

Chunked Requests
  Since the Servlet API does not expose the individual request parts of chunked requests to a servlet there is no way
  *spray-servlet* can pass them through to the application. The way chunked requests are handled is completely up to
  the servlet container.

Chunked Responses
  *spray-can* renders ``ChunkedResponseStart``, ``MessageChunks`` and ``ChunkedMessageEnd`` messages directly to
  "the wire". Since the Servlet API operates on a somewhat higher level of abstraction *spray-servlet* can only write
  these messages to the servlet container one by one, with ``flush`` calls in between. The way the servlet container
  interprets these calls is up to its implementation.

*Closed* Messages
  The Servlet API completely hides the actual management of the HTTP connections from the application. Therefore the
  connector servlet has no way of finding out whether a connection was closed or not. In order to provide a similar
  message protocol as *spray-can* the connector servlet therefore simply assumes that all connections are closed after
  the final part of a response has been written, no matter whether the servlet container actually uses persistent
  connections or not.

Timeout Semantics
  When working with chunked responses the semantics of the ``request-timeout`` config setting are different.
  In *spray-can* it designates the maximum time, in which a response must have been *started* (i.e. the first chunk
  received), while in *spray-servlet* it defines the time, in which the response must have been *completed* (i.e. the
  last chunk received).

HTTP Pipelining & SSL Support
  Whether and how HTTP pipelining and SSL/TLS encryption are supported depends on the servlet container implementation.


Example
-------

The `/examples/spray-servlet/`__ directory of the *spray* repository
contains a number of example projects for *spray-servlet*.

.. __: https://github.com/spray/spray/tree/release/1.1/examples/spray-servlet


simple-spray-servlet-server
~~~~~~~~~~~~~~~~~~~~~~~~~~~

This example implements a very simple web-site built on top of *spray-servlet*.
It shows off various features like streaming and timeout handling.

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project simple-spray-servlet-server" container:start shell

4. Browse to http://127.0.0.1:8080/

5. Alternatively you can access the service with ``curl``::

    curl -v 127.0.0.1:8080/ping

6. Stop the service with::

    container:stop
