.. _-alwaysCache-:

alwaysCache
===========

Wraps its inner Route with caching support using the given ``spray.caching.Cache`` implementation and
the in-scope keyer function.

Signature
---------

::

    def alwaysCache(cache: Cache[CachingDirectives.RouteResponse])
                   (implicit keyer: CacheKeyer, factory: ActorRefFactory): Directive0

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

Like :ref:`-cache-` but doesn't regard a ``Cache-Control`` request header for deciding if the cache should be circumvented.

.. note:: Caching directives are not automatically in scope, see :ref:`Caching Directive Usage` about how to enable them.

Example
-------

.. includecode:: ../code/docs/directives/CachingDirectivesExamplesSpec.scala
   :snippet: alwaysCache
