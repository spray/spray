.. _-alwaysCache-:

alwaysCache
===========

Wraps its inner Route with caching support using the given ``spray.caching.Cache`` implementation and
the in-scope keyer function.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/CachingDirectives.scala
   :snippet: alwaysCache

Description
-----------

Like :ref:`-cache-` but doesn't regard a ``Cache-Control`` request header for deciding if the cache should be circumvented.

.. note:: Caching directives are not automatically in scope, see :ref:`Caching Directive Usage` about how to enable them.

Example
-------

.. includecode:: ../code/docs/directives/CachingDirectivesExamplesSpec.scala
   :snippet: alwaysCache
