.. _spray-testkit:

spray-testkit
=============

One of *sprays* core design goals is good testability of the created services. Since actor-based systems can sometimes
be cumbersome to test *spray* fosters the separation of processing logic from actor code in most of its modules.

For services built with :ref:`spray-routing` *spray* provides a dedicated test DSL that makes actor-less testing of
route logic easy and convenient. This "route test DSL" is made available with the *spray-testkit* module.


Dependencies
------------

Apart from the Scala library (see :ref:`current-versions` chapter) *spray-testkit* depends on

- :ref:`spray-http`
- :ref:`spray-httpx`
- :ref:`spray-routing`
- :ref:`spray-util`
- akka-actor (with 'provided' scope, i.e. you need to pull it in yourself)
- scalatest_ (with 'provided' scope, for the ``ScalatestRouteTest``)
- specs2_ (with 'provided' scope, for the ``Specs2RouteTest``)


Installation
------------

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-caching* into your classpath.
However, since you normally don't need to have access to *spray-caching* from your production code, you should limit
the dependency to the ``test`` scope::

    libraryDependencies += "io.spray" % "spray-can" % version % "test"

Currently *spray-testkit* supports the two most popular scala testing frameworks, scalatest_ and specs2_. Depending on
which one you are using you need to mix either the ``ScalatestRouteTest`` or the ``Specs2RouteTest`` trait into your
test specification.

.. _scalatest: http://scalatest.org/
.. _specs2: http://etorreborre.github.com/specs2/


Usage
-----

Here is an example of what a simple test with *spray-testkit* might look like:

.. includecode:: code/docs/FullTestKitExampleSpec.scala
   :snippet: source-quote

The basic structure of a test built with *spray-testkit* is this (expression placeholder in all-caps)::

    REQUEST ~> ROUTE ~> check {
      ASSERTIONS
    }

In this template *REQUEST* is an expression evaluating to an ``HttpRequest`` instance. Since both *RouteTest* traits
extend the *spray-httpx* :ref:`RequestBuilding` trait you have access to its mini-DSL for convenient and concise request
construction.

*ROUTE* is an expression evaluating to a *spray-routing* ``Route``. You can specify one inline or simply refer to the
route structure defined in your service.

The final element of the ``~>`` chain is a ``check`` call, which takes a block of assertions as parameter. In this block
you define your requirements onto the result produced by your route after having processed the given request. Typically
you use one of the defined "inspectors" to retrieve a particular element of the routes response and express assertions
against it using the test DSL provided by your test framework. For example, with specs2_, in order to verify that your
route responds to the request with a status 200 response, you'd use the ``status`` inspector and express an assertion
like this::

    status mustEqual 200

The following inspectors are defined:

``handled: Boolean``
  Indicates whether the route produced an ``HttpResponse`` for the request. If the route rejected the
  request ``handled`` evaluates to ``false``.

``response: HttpResponse``
  The ``HttpResponse`` returned by the route. If the route did not return an ``HttpResponse`` instance (e.g. because it
  rejected the request) a test failure is triggered.

``rejections: List[Rejection]``
  The rejections produced by the route. If the route did not reject the request a test failure is triggered.

``rejection: Rejection``
  The rejection produced by the route. If the route did not produce exactly one rejection a test failure is triggered.

``status: StatusCode``
  Identical to ``response.status``

``headers: List[HttpHeader]``
  Identical to ``response.headers``

``header[T <: HttpHeader :ClassManifest]: Option[T]``
  Identical to ``response.header[T]``

``header(name: String): Option[HttpHeader]``
  Returns the response header with the given name or ``None`` if no such header can be found.

``entityAs[T :Unmarshaller]: T``
  Unmarshals the response entity using the in-scope ``Unmarshaller`` for the given type. Any errors in the process
  trigger a test failure.

``body: HttpBody``
  Returns the contents of the response entity. If the response entity is empty a test failure is triggered.

``contentType: ContentType``
  Identical to ``body.contentType``.

``mediaType: MediaType``
  Identical to ``contentType.mediaType``

``charset: HttpCharset``
  Identical to ``contentType.charset``

``definedCharset: Option[HttpCharset]``
  Identical to ``contentType.definedCharset``

``chunks: List[MessageChunk]``
  Returns the list of message chunks produced by the route.

``closingExtensions: List[ChunkExtension]``
  Returns the list of chunk extensions the route produced with a ``ChunkedMessageEnd`` response part.

``trailer: List[HttpHeader]``
  Returns the list of trailer headers the route produced with a ``ChunkedMessageEnd`` response part.


Examples
--------

A full example of how an API service definition can be structured in order to be testable with *spray-testkit* and
without actor involvement is shown with the :ref:`simple-on-spray-can` example. This__ is its test definition.

__ https://github.com/spray/spray/blob/master/examples/spray-routing/simple-on-spray-can/src/test/scala/spray/examples/DemoServiceSpec.scala

Another great pool of examples are the tests for all the predefined directives in :ref:`spray-routing`.
They can be found here__.

__ https://github.com/spray/spray/tree/master/spray-routing-tests/src/test/scala/spray/routing