.. _-cache-:

cache
=====

Wraps its inner Route with caching support using the given ``spray.caching.Cache`` implementation and
the in-scope keyer function.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/CachingDirectives.scala
   :snippet: cache

The ``CacheSpecMagnet`` constructor:[1]_

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/CachingDirectives.scala
   :snippet: CacheSpecMagnet

The ``routeCache`` constructor for caches:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/CachingDirectives.scala
   :snippet: route-Cache

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

The directive tries to serve the request from the given cache and only if not found runs the inner route to generate a
new response. A simple cache can be constructed using ``routeCache`` constructor.

The directive is implemented in terms of :ref:`-cachingProhibited-` and :ref:`-alwaysCache-`. This means that clients
can circumvent the cache using a ``Cache-Control`` request header. This behavior may not be adequate depending on your
backend implementation (i.e how expensive a call circumventing the cache into the backend is). If you want to force all
requests to be handled by the cache use the :ref:`-alwaysCache-` directive instead. In complexer cases, e.g. when the
backend can validate that a cached request is still acceptable according to the request `Cache-Control` header the
predefined caching directives may not be sufficient and a custom solution is necessary.

.. note:: Caching directives are not automatically in scope, see :ref:`Caching Directive Usage` about how to enable them.

Example
-------

.. includecode:: ../code/docs/directives/CachingDirectivesExamplesSpec.scala
   :snippet: cache-0
