.. _spray-client:

spray-client
============

*spray-client* provides high-level HTTP client functionality by adding another logic layer on top of the rather
low-level :ref:`HttpClient` that is part of the :ref:`spray-can` module. It sports the following features:

- Connection pooling
- Rich, immutable HTTP model
- Compression / Decompression
- Marshalling / Unmarshalling from and to your custom types
- HTTP Basic Auth
- Configurable retry behavior
- Full support for HTTP pipelining
- Actor-based architecture for easy integration into your Akka applications

Currently, HTTP streaming (i.e. chunked transfer encoding) is not yet fully supported on the *spray-client* level
(even though the underlying *spray-can* :ref:`HttpClient` does support it), i.e. you cannot send chunked requests and
the ``spray.can.client.response-chunk-aggregation-limit`` config setting must be non-zero).


Dependencies
------------

Apart from the Scala library (see :ref:`current-versions` chapter) *spray-client* depends on

- :ref:`spray-can`
- :ref:`spray-http`
- :ref:`spray-httpx`
- :ref:`spray-util`
- akka-actor (with 'provided' scope, i.e. you need to pull it in yourself)


Installation
------------

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-client* into your classpath.

Afterwards just ``import spray.client._`` to bring all relevant identifiers into scope.


Configuration
-------------

Just like Akka *spray-servlet* relies on the `typesafe config`_ library for configuration. As such its JAR contains a
``reference.conf`` file holding the default values of all configuration settings. In your application you typically
provide an ``application.conf``, in which you override Akka and/or *spray* settings according to your needs.

.. note:: Since *spray* uses the same configuration technique as Akka you might want to check out the
   `Akka Documentation on Configuration`_.

.. _typesafe config: https://github.com/typesafehub/config
.. _Akka Documentation on Configuration: http://doc.akka.io/docs/akka/2.0.3/general/configuration.html

This is the ``reference.conf`` of the *spray-client* module:

.. literalinclude:: /../spray-client/src/main/resources/reference.conf
   :language: bash


Basic Usage
-----------

The central element in *spray-client* is the ``HttpConduit``, which is an abstraction over a number of HTTP connections
to *one* specific HTTP server (the underlying *spray-can* :ref:`HttpClient` works on the level of one single HTTP
connection). An ``HttpConduit`` can be reused for all requests to a specific host that an application would like to
fire, but it cannot be "re-targeted" to a different host. However, since an ``HttpConduit`` is light-weight you can
simply create a new instance for every new target server the application needs to talk to.

The ``HttpConduit`` is a regular actor, so you create one like this:

.. includecode:: code/docs/HttpConduitExamplesSpec.scala
   :snippet: setup

Once you have a reference to a conduit you typically create a "pipeline" around it. In the simplest case this looks like
this:

.. includecode:: code/docs/HttpConduitExamplesSpec.scala
   :snippet: simple-pipeline

In this case the pipeline has the type ``HttpRequest => Future[Response]``, so you can use it like this:

.. includecode:: code/docs/HttpConduitExamplesSpec.scala
   :snippet: response-future


Message Pipeline
----------------

A pipeline of type ``HttpRequest => Future[HttpResponse]`` is nice start but leaves the creation of requests and
interpretation of responses completely to you. Many times you actually want to send and/or receive custom objects that
need to be serialized to HTTP requests or deserialized from HTTP responses. *spray-client* supports this via the
concept of a "Message Pipeline".

Check out this snippet:

.. includecode:: code/docs/HttpConduitExamplesSpec.scala
   :snippet: example-2

This defines a more complex pipeline that takes an ``HttpRequest``, adds headers and compresses its entity before
dispatching it to the target server (the ``sendReceive`` element of the pipeline). The response coming back is then
decompressed and its entity unmarshalled.

If you ``import HttpConduit._`` you not only get easy access to ``sendReceive`` but also all elements of the
:ref:`RequestBuilding` trait, which is mixed in by the ``HttpConduit`` companion object. Therefore you can easily create
requests via something like ``Post("/orders", Order(42))``, which is not only shorter but also provides for
automatic marshalling of custom types.


Dispatch Strategy
-----------------

Apart from the underlying :ref:`HttpClient` instance to use and the target servers hostname/port the ``HttpConduit``
constructor takes a ``DispatchStrategy`` instance (with ``NonPipelined`` being the default).
A ``DispatchStrategy`` determines how outgoing requests are scheduled across the several connections the conduit
manages. Currently *spray-client* comes with two predefined strategies, which schedule requests across connections
via the following logic:

NonPipelined (default)
  - Dispatch to the first idle connection in the pool, if there is one.
  - If none is idle, dispatch to the first unconnected connection, if there is one.
  - If all are already connected, store the request and send it as soon as one
    connection becomes either idle or unconnected.

Pipelined
  - Dispatch to the first idle connection in the pool, if there is one.
  - If none is idle, dispatch to the first unconnected connection, if there is one.
  - If all are already connected, dispatch to the connection with the least open requests.


Error Handling
--------------

In case of errors an ``HttpConduit`` will retry requests up to the number of times configured as ``max-retries``, before
completing the response future with an exception.

.. note:: Only idempotent requests, as defined by the HTTP spec, are retried. Requests with HTTP methods ``POST`` or
   ``PATCH`` are not considered idempotent and therefore *never* retried.


Example
-------

The ``/examples/spray-client/`` directory of the *spray* repository
contains a number of example projects for *spray-client*.


simple-spray-client
~~~~~~~~~~~~~~~~~~~

This example shows off how to use *spray-client* by performing two things

- fetch the github homepage (a very simple GET)
- query the Googles Elevation API to retrieve the elevation of Mt. Everest

Follow these steps to run it on your machine:

1. Clone the *spray* repository::

    git clone git://github.com/spray/spray.git

2. Change into the base directory::

    cd spray

3. Run SBT::

    sbt "project simple-spray-client" run