What is *spray*?
================

*spray* is a suite of lightweight Scala_ libraries providing client- and server-side **REST**/**HTTP** support
on top Akka_.

We believe that, having chosen Scala_ (and possibly Akka_) as primary tools for building software, you'll want to rely
on their power not only in your application layer but throughout the full (JVM-level) network stack. *spray* provides
just that: a set of integrated components for all your REST/HTTP needs that let you work with idiomatic Scala_
(and Akka_) APIs at the stack level of your choice, all implemented without any wrapping layers around "legacy"
Java libraries.

.. _scala: http://www.scala-lang.org
.. _akka: http://akka.io


Principles
----------

*sprays* development is guided by the following principles:

Fully asynchronous, non-blocking
  All APIs are fully asynchronous, blocking code is avoided wherever at all possible.

Actor- and Future-based
  *spray* fully embraces the programming model of the platform it is built upon.
  Akka Actors and Futures are key constructs of its APIs.

High-performance
  Especially *sprays* low-level components are carefully crafted for excellent performance in high-load environments.

Lightweight
  All dependencies are very carefully managed, *sprays* codebase itself is kept as lean as possible.

Modular
  Being structured into a set of integrated but loosely coupled components your application only needs to depend onto
  the parts that are actually used.

Testable
  All *spray* components are structured in a way that allows for easy and convenient testing.


Modules
-------

Currently the *spray* suite consists of these modules:

:ref:`spray-caching`
  Fast and lightweight in-memory caching built upon concurrentlinkedhashmap_ and Akka Futures.

:ref:`spray-can`
  A low-level, low-overhead, high-performance HTTP server and client built on top of :ref:`spray-io`.

:ref:`spray-client`
  Provides client-side HTTP support at a higher level than the low-level :ref:`spray-can` :ref:`HTTP Client APIs`,
  which it builds on.

:ref:`spray-http`
  An immutable model of HTTP requests, responses and common headers. This module is completely stand-alone, it
  neither depends on Akka_ nor on any other part of *spray*.

:ref:`spray-httpx`
  Higher-level tools for working with HTTP messages (mainly marshalling, unmarshalling and (de)compression)
  that are used by both :ref:`spray-client` as well as :ref:`spray-routing`.

:ref:`spray-io`
  A low-level network IO layer for directly connecting Akka actors to asynchronous Java NIO sockets. We like to think of
  it a basic version of Netty_ for Scala. As of 1.0-M8/1.1-M8 it contains a backport of the new Akka IO layer coming
  with Akka 2.2. In 1.2-M8 it merely contains a few spray-specific "left-overs" that will likely go away completely in
  the future.

:ref:`spray-servlet`
  An adapter layer providing (a subset of) the *spray-can* :ref:`HTTP Server` interface on top of the Servlet API.
  Enables the use of :ref:`spray-routing` in a servlet container.

:ref:`spray-routing`
  A high-level routing DSL for elegantly defining RESTful web services.

:ref:`spray-testkit`
  A DSL for easily testing :ref:`spray-routing` services. Supports both ScalaTest_ as well as Specs2_.

:ref:`spray-util`
  Small utility module used by all other modules except :ref:`spray-http`.

spray-json_
  A lightweight, clean and simple JSON implementation in Scala. Because it neither depends on any other part of *spray*
  nor on Akka and is only an optional dependency of :ref:`spray-client` and :ref:`spray-httpx` it doesn't live in
  the main *spray* repository, but rather in `its own github repository`__
  Note that you can easily use *spray* with whatever JSON library you like best, *spray-json* is just one of several
  alternatives.

__ spray-json_
.. _spray-json: https://github.com/spray/spray-json
.. _concurrentlinkedhashmap: http://code.google.com/p/concurrentlinkedhashmap/
.. _netty: http://www.jboss.org/netty
.. _scalatest: http://scalatest.org
.. _specs2: http://specs2.org


Philosophy
----------

Since its inception in early 2011 *sprays* development has been driven with a clear focus on providing tools for
building integration layers rather than application cores. As such it regards itself as a suite of *libraries* rather
than a framework.

A framework, as we'd like to think of the term, gives you a "frame", in which you build your application. It comes with
a lot of decisions already pre-made and provides a foundation including support structures that lets you get started
and deliver results quickly. In a way a framework is like a skeleton onto which you put the "flesh" of your application
in order to have it come alive. As such frameworks work best if you choose them *before* you start application
development and try to stick to the frameworks "way of doing things" as you go along.

For example, if you are building a browser-facing web application it makes sense to choose a web framework and build
your application on top of it because the "core" of the application is the interaction of a browser with your code on
the web-server. The framework makers have chosen one "proven" way of designing such applications and let you "fill in
the blanks" of a more or less flexible "application-template". Being able to rely on best-practice architecture like
this can be a great asset for getting things done quickly.

However, if your application is not primarily a web application because its core is not browser-interaction but
some specialized maybe complex business service and you are merely trying to connect it to the world via a REST/HTTP
interface a web-framework might not be what you need. In this case the application architecture should be dictated by
what makes sense for the core not the interface layer. Also, you probably won't benefit from the possibly existing
browser-specific framework components like view templating, asset management, JavaScript- and CSS
generation/manipulation/minification, localization support, AJAX support, etc.

*spray* was designed specifically as "not-a-framework", not because we don't like frameworks, but for use cases where
a framework is not the right choice. *spray* is made for building integration layers based on HTTP and as such tries
to "stay on the sidelines". Therefore you normally don't build your application "on top of" *spray*, but you build your
application on top of whatever makes sense and use *spray* merely for the HTTP integration needs.