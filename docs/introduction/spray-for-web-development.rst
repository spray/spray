spray for Web Development
=========================

Even though *sprays* development focus so far has not been web applications but HTTP-based integration layers, you can
of course use it for powering browser-based GUIs as well. The recent trend of moving web application logic more and more
away from the server and into the (JS-based) browser client as well as the increasing availability of good SBT-plugins
for things *spray* itself does not provide (like view-templating or LESS- and CoffeeScript-Support)
might even make such an approach gain attractiveness.

Currently a *spray*-based web development stack might consist of (a subset of) these components:

:ref:`spray-can` :ref:`HTTP Server`
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
Of course the downside of this approach is that integrating the different components is now on your shoulders. Also,
there is no single point of contact for support and upgrades.

Still, combining a client-side JavaScript framework with a *spray*-based application backend could prove itself as an
interesting alternative to a "classic" server-side web framework. We'd love to hear about your experiences in this
regard...


This Site
---------

One example of a simple website running on a *spray*-based stack is this site (http://spray.io). Here is the stack we
use for *spray.io*:

- :ref:`spray-can` :ref:`HTTP Server`
- :ref:`spray-routing`
- sbt-revolver_
- twirl_
- a custom SBT extension for compiling Sphinx_ sources to JSON
- spray-json_ (for reading Sphinx_ output)
- sbt-assembly_
- Mentor_ (a non-free, responsive HTML5 template based on Bootstrap_)

For more details check out the route definition of this site:
https://github.com/spray/spray/blob/master/site/src/main/scala/spray/site/SiteServiceActor.scala.


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
.. _Bootstrap: http://twitter.github.com/bootstrap/