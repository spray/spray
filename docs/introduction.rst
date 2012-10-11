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

.. _scala: http://scala-lang.org
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


Modules
-------

Currently the *spray* suite consists of these modules:

:ref:`spray-caching`
  Fast and lightweight in-memory caching built upon concurrentlinkedhashmap_ and Akka Futures.

:ref:`spray-can`
  A low-level, low-overhead, high-performance HTTP server and client built on top of :ref:`spray-io`.

:ref:`spray-client`
  An HTTP client providing a higher-level interface than the low-level :ref:`spray-can` :ref:`HttpClient`,
  which it builds on.

:ref:`spray-http`
  An immutable model of HTTP requests, responses and common headers. This module is completely stand-alone, it
  neither depends on Akka_ nor on any other part of *spray*.

:ref:`spray-httpx`
  Higher-level tools for working with HTTP messages (mainly marshalling, unmarshalling and (de)compression)
  that are used by both :ref:`spray-client` as well as :ref:`spray-routing`.

:ref:`spray-io`
  A low-level network IO layer for directly connecting Akka actors to asynchronous Java NIO sockets. It sports a
  pipelined architecture including predefined reusable pipeline stages (like connection timeouts and SSL/TLS support).
  We like to think of it a basic version of Netty_ for Scala.

:ref:`spray-servlet`
  An adapter layer providing (a subset of) the *spray-can* :ref:`HttpServer` interface on top of the Servlet API.
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

__ spray-json_
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
a lot of decisions already pre-made and provides a foundation including support structures, that lets you get started
and deliver results quickly. In a way a framework is like a skeleton onto which you put the "flesh" of your application
in order to have it come alive. As such frameworks work best if you choose them *before* you start application
development and try to stick to the frameworks "way of doing things" as you go along.

For example, if you are building a browser-facing web application it makes sense to choose a web framework and build
your application on top of it, because the "core" of the application is the interaction of a browser with your code on
the web-server. The framework makers have chosen one "proven" way of designing such applications and let you "fill in
the blanks" of a more or less flexible "application-template". Being able to rely on "best-practice" architecture like
this can be a great asset for getting things done quickly.

However, if your application is not primarily a web application, because its core is not browser-interaction but
some specialized, maybe complex business service, and you are merely trying to connect it to the world via a REST/HTTP
interface, a web-framework might not be what you need. In this case the application architecture should be dictated by
what makes sense for the core, not the interface layer. Also, you probably won't benefit of the possibly existing
browser-specific framework components, like view templating, asset management, JavaScript- and CSS
generation/manipulation/minification, localization support, AJAX support, etc.

*spray* was designed specifically as "not-a-framework", not because we don't like frameworks, but for use cases where
a framework is not the right choice. *spray* is made for building integration layers based on HTTP and as such tries
to "stay on the sidelines". Therefore you normally don't build your application "on top of" *spray*, but you build your
application on top of whatever makes sense and use *spray* merely for the HTTP integration needs.


spray for Web Development
-------------------------

Even though *sprays* development focus so far has not been web applications, but HTTP-based integration
layers, you can of course use it for powering browser-based GUIs as well. The recent trend of moving web application
logic more and more away from the server and into the (JS-based) browser client as well as the increasing availability
of good SBT-plugins for things *spray* itself does not provide (like view-templating or LESS- and CoffeeScript-Support)
might even make such an approach gain attractiveness.

Currently a *spray*-based web development stack might consist of (a subset of) these components:

:ref:`spray-can` :ref:`HttpServer`
  The web-server. Receives HTTP request and sends out responses. Optionally terminates SSL.

:ref:`spray-routing`
  The routing layer. Handles requests depending on URI, parameters, content, etc. and (un)marshals to and from the
  application-specific domain model. Forwards higher-level job requests to deeper application levels and converts
  the respective results into HTTP responses. Serves static content.

sbt-revolver_
  SBT-plugin for hot reloading of changes to any type of sources (scala, twirl, CSS, LESS, JavaScript, CoffeeScript,
  images, etc.) without the need to restart the server. Can deliver an almost "dynamic-language" type of development
  experience.

twirl_
  SBT-plugin providing for view-templating based on the `play 2.0`_ template engine.

less-sbt_
  SBT-plugin for compilation of LESS_ sources to CSS.

coffeescripted-sbt_
  SBT-plugin for compilation of CoffeeScript_ sources to JavaScript.

sbt-js_
  SBT-plugin for Javascript and Coffeescript compilation, minification, and templating.

SLICK_
  Library for elegant database query and access.

spray-json_
  Library for clean and idiomatic JSON reading and writing.

sbt-assembly_
  SBT-plugin for single-fat-JAR-deployment.

`A client-side frontend framework`_
  One of the several established client-side JavaScript frameworks.


While a stack like this might not provide everything that a full-grown web framework can offer it could have all that's
required for your particular application. And, because you can pick the best tool for each individual job, the resulting
application stack is a lot more flexible and possibly future-proof than any single framework.
Of course, the downside of this approach is that integrating the different components is now on your shoulders. Also,
there is no single point of contact for support and upgrades.

Still, combining a client-side JavaScript framework with a *spray*-based application backend could prove itself as an
interesting alternative to a "classic", server-side web framework. We'd love to hear about your experiences in this
regard...


This Site
~~~~~~~~~

One example of a simple website running on a *spray*-based stack is this site (http://spray.io). Here is the stack we
use for *spray.io*:

- :ref:`spray-can` :ref:`HttpServer`
- :ref:`spray-routing`
- sbt-revolver_
- twirl_
- a custom SBT extension for compiling Sphinx_ sources to JSON
- spray-json_ (for reading Sphinx_ output)
- sbt-assembly_
- Mentor_ (a simple **non-free** HTML5 template)


.. _sbt-revolver: https://github.com/spray/sbt-revolver
.. _twirl: https://github.com/spray/twirl
.. _play 2.0: http://www.playframework.org/
.. _less-sbt: https://github.com/softprops/less-sbt
.. _LESS: http://lesscss.org/
.. _coffeescripted-sbt: https://github.com/softprops/coffeescripted-sbt
.. _CoffeeScript: http://coffeescript.org/
.. _sbt-js: https://github.com/untyped/sbt-plugins/tree/master/sbt-js
.. _SLICK: http://slick.typesafe.com/
.. _spray-json: https://github.com/spray/spray-json
.. _sbt-assembly: https://github.com/sbt/sbt-assembly
.. _A client-side frontend framework: http://blog.stevensanderson.com/2012/08/01/rich-javascript-applications-the-seven-frameworks-throne-of-js-2012/
.. _Sphinx: http://sphinx.pocoo.org/
.. _Mentor: http://demo.pixelentity.com/?mentor_html