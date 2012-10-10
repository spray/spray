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

.. _scalatest: http://scalatest.org/
.. _specs2: http://etorreborre.github.com/specs2/


Installation
------------

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-caching* into your classpath.
However, since you normally don't need to have access to *spray-caching* from your production code, you should limit
the dependency to the ``test`` scope::

    libraryDependencies += "cc.spray" % "spray-can" % version % "test"


Usage
-----

Here is an example of what a simple test with *spray-testkit* might look like:

.. includecode:: code/docs/FullTestKitExampleSpec.scala
   :snippet: source-quote