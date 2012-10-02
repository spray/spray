.. _spray-servlet:

spray-servlet
=============

*spray-servlet* is an adapter layer providing (a subset of) the *spray-can* :ref:`HttpServer` interface on top of the
Servlet API. As one main application it enables the use of :ref:`spray-routing` in a servlet container.


Dependencies
------------

Apart from the Scala library (see :ref:`current-versions` chapter) *spray-can* depends on

.. rst-class:: tight

- :ref:`spray-http`
- :ref:`spray-util`
- akka-actor (with 'provided' scope, i.e. you need to pull it in yourself)
- the Servlet-3.0 API (with 'provided' scope, usually automatically available from your servlet container)


Installation
------------

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-util* into your classpath.


Configuration
-------------

Just like Akka *spray-servlet* relies on the `typesafe config`_ library for configuration. As such its JAR contains a
``reference.conf`` file holding the default values of all configuration settings. In your application you typically
provide an ``application.conf``, in which you override Akka and/or *spray* settings according to your needs.

.. note:: Since *spray* uses the same configuration technique as Akka you might want to check out the
   `Akka Documentation on Configuration`_.

.. _typesafe config: https://github.com/typesafehub/config
.. _Akka Documentation on Configuration: http://doc.akka.io/docs/akka/2.0.3/general/configuration.html

This is the ``reference.conf`` of the *spray-servlet* module:

.. literalinclude:: /../spray-servlet/src/main/resources/reference.conf
   :language: bash


Basic Architecture
------------------

The central element of *spray-servlet* is the ``Servlet30ConnectorServlet``. Its job is to accept incoming HTTP
requests, suspend them (using Servlet 3.0 ``startAsync``), create immutable :ref:`spray-http` ``HttpRequest`` instances
for them and dispatch these to a service actor provided by the application.

The messaging API as seen from the application is modeled as closely as possible like its counterpart, the
*spray-can* :ref:`HttpServer`.

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

The ``web.xml`` registers a ``ServletContextListener`` (``cc.spray.servlet.Initializer``), which initializes the
application when the servlet is started. The ``Initializer`` loads the configured ``boot-class`` and instantiates it
using the default constructor, which must be available. The boot class must implement the ``WebBoot`` trait, which is
defined like this:

.. includecode:: /../spray-servlet/src/main/scala/cc/spray/servlet/WebBoot.scala
   :snippet: source-quote

A very basic boot class implementation is `this one`__ from the `simple-spray-servlet-server`_ example.

__ https://github.com/spray/spray/blob/master/examples/spray-servlet/simple-spray-servlet-server/src/main/scala/cc/spray/examples/Boot.scala

The boot class is responsible for creating the Akka ``ActorSystem`` for the application as well as the service actor.
When the application is shut down by the servlet container the ``Initializer`` shuts down the ``ActorSystem``, which
cleanly terminates all application actors including the service actor.


Message Protocol
----------------

Just like in its counterpart, the *spray-can* :ref:`HttpServer`, all communication between the connector servlet and
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

.. caution:: Since the ``ActorRef`` used as the sender of a request is an :ref:`UnregisteredActorRef` it is not
 reachable remotely. This means that the service actor needs to live in the same JVM as the connector servlet.


Chunked Responses
~~~~~~~~~~~~~~~~~

Alternatively to a single ``HttpResponse`` instance the handler can choose to respond to the request sender with the
following sequence of individual messages:

.. rst-class:: tight

- One ``ChunkedResponseStart``
- Zero or more ``MessageChunks``
- One ``ChunkedMessageEnd``

The connector servlet writes the individual response parts into the servlet response ``OutputStream`` and flushes it.
Whether these parts are really rendered "to the wire" as chunked message parts depends on the servlet container
implementation. The Servlet API has not dedicated support for chunked responses.


Request Timeouts
~~~~~~~~~~~~~~~~

If the service actor does not complete a request within the configured ``request-timeout`` period a
``cc.spray.http.Timeout`` message is sent to the timeout handler, which can be the service actor itself or
another actor (depending on the ``timeout-handler`` config setting). The timeout handler then has the chance to
complete the request within the time period configured as ``timeout-timeout``. Only if the timeout handler also misses
its deadline for completing the request will the connector servlet complete the request itself with a "hard-coded"
error response (which you can change by overriding the ``timeoutResponse`` method).


Send Confirmations
~~~~~~~~~~~~~~~~~~

If not disabled via the ``ack-sends`` config setting the connector servlet replies with a DefaultIOSent__ message to the
sender of a response (part) as soon as it has been successfully passed on to the servlet container
(The ``DefaultIOSent`` message implements the same ``IOSent`` marker interface as its ``SentOk`` counterpart used by the
*spray-can* :ref:`HttpServer`).
This confirmation message can be used, for example, to trigger the sending of the next response part in a response
streaming scenario. With such a design the application will never produce more data than the servlet container can
handle.

__ https://github.com/spray/spray/blob/master/spray-util/src/main/scala/cc/spray/util/model/IOSent.scala


Error Messages
~~~~~~~~~~~~~~

Any exception that the container throws when the connector servlet tries to write a response (part) to it is wrapped
in a ``ServletError`` instance and sent back to the sender of the response (part):

.. includecode:: /../spray-servlet/src/main/scala/cc/spray/servlet/ServletError.scala
   :snippet: source-quote


HTTP Headers
------------

The connector servlet always passes all received headers on to the application. Additionally the values of the
``Content-Length`` and ``Content-Type`` headers are interpreted by the servlet itself. All other headers are of no
interest to it.

Also, if your ``HttpResponse`` instances include a ``Content-Length`` or ``Content-Type`` header they will be ignored
and *not* written through to the servlet container (as the connector servlet sets these response headers itself).

.. note:: The ``Content-Type`` header has special status in *spray* since its value is part of the ``HttpEntity`` model
   class. Even though the header also remains in the ``headers`` list of the ``HttpRequest`` *sprays* higher layers
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

``Closed`` Messages
  The Servlet API completely hides the actual management of the HTTP connections from the application. Therefore the
  connector servlet has no way of finding out whether a connection was closed or not and thus does not send any
  ``Closed`` messages to the application in the way the *spray-can* :ref:`HttpServer` does.

Timeout Semantics
  When working with chunked responses the semantics of the ``request-timeout`` config setting are different.
  In *spray-can* it designates the maximum time, in which a response must have been *started* (i.e. the first chunk
  received), while in *spray-servlet* it defines the time, in which the response must have been *completed* (i.e. the
  last chunk received).

Connection Configuration
  *spray-servlet* does not allow for the dynamic reconfiguration of the various timeout settings in the way *spray-can*
  does.

HTTP Pipelining & SSL Support
  Whether and how HTTP pipelining and SSL/TLS encryption are supported depends on the servlet container implementation.


Example
-------

The ``/examples/spray-servlet/`` directory of the *spray* repository
contains a number of example projects for *spray-servlet*.


simple-spray-servlet-server
~~~~~~~~~~~~~~~~~~~~~~~~~~~

This examples implements a very simple web-site built on top of *spray-servlet*.
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
