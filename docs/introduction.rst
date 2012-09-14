Introduction
============

What is *spray*?
----------------

*spray* is a suite of lightweight Scala_ libraries providing client- and server-side **REST**/**HTTP** support as well
as low-level network IO on top Akka_.

We believe that, having chosen Scala_ (and possibly Akka_) as primary tools for building software, you'll want to rely
on their power not only in your application layer but throughout the full (JVM-level) network stack. *spray* provides
just that: a set of integrated components for all your REST/HTTP and network IO needs that let you work with idiomatic
Scala_ (and Akka_) APIs at the stack level of your choice, all implemented without any wrapping layers around "legacy"
Java libraries.


Principles
----------

*sprays* development is guided by the following principles:

Fully asynchronous, non-blocking
  All APIs are fully asynchronous, blocking code is avoided wherever at all possible.

Actor- and Future-based
  *spray* fully embraces the programming model of the platform it is built upon.
  Akka_ Actors and Futures are key constructs of its APIs.

High-performance
  Especially *sprays* low-level components are carefully crafted for excellent performance in high-load environments.

Lightweight
  All dependencies are very carefully managed, *sprays* codebase itself is kept as lean as possible.

Modular
  Being structured into a set of integrated but loosely coupled components your application only needs to depend onto
  the parts that are actually used.


Modules
-------

Currently the *spray* suite consists of these modules:

:ref:`spray-caching`
  Fast and lightweight in-memory caching built upon concurrentlinkedhashmap_ and Akka_ Futures.

:ref:`spray-can`
  A low-level, low-overhead, high-performance HTTP server and client built on top of :ref:`spray-io`.

:ref:`spray-client`
  An HTTP client providing a higher-level interface than the low-level :ref:`spray-can` ``HttpClient``,
  which it builds on.

:ref:`spray-http`
  An immutable model of HTTP requests, responses and common headers. This module is completely stand-alone, it
  neither depends on Akka_ nor on any other part of *spray*.

:ref:`spray-httpx`
  Higher-level tools for working with HTTP messages (mainly marshalling, unmarshalling and (de)compression)
  that are used by both :ref:`spray-client` as well as :ref:`spray-routing`.

:ref:`spray-io`
  A low-level network IO layer for directly connecting Akka_ actors to asynchronous Java NIO sockets. It sports a
  pipelined architecture including predefined reusable pipeline stages (like connection timeouts and SSL/TLS support).
  We like to think of it a basic version of Netty_ for Scala.

:ref:`spray-servlet`
  An adapter layer providing (a subset of) the :ref:`spray-can` server interface on top of the Servlet API. With it
  you can use :ref:`spray-routing` in a servlet container.

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

__ spray-json_


Philosophy
----------

Since *spray* is a tool for building integration layers rather than application cores it regards itself as a suite of
*libraries* rather than a framework. This is to express the idea of "staying on the sidelines" of your application by
as much as possible, so that it doesn't restrict you in the way you build your application.


.. _scala: http://scala-lang.org
.. _akka: http://akka.io
.. _concurrentlinkedhashmap: http://code.google.com/p/concurrentlinkedhashmap/
.. _netty: http://www.jboss.org/netty
.. _scalatest: http://scalatest.org
.. _specs2: http://specs2.org
.. _spray-json: https://github.com/spray/spray-json