.. _-cachingProhibited-:

cachingProhibited
=================

Passes only requests that explicitly forbid caching with a ``Cache-Control`` header with either a ``no-cache`` or
``max-age=0`` setting.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/CachingDirectives.scala
   :snippet: cachingProhibited

Description
-----------

This directive is used to filter out requests that forbid caching. It is used as a building block of the :ref:`-cache-`
directive to prevent caching if the client requests so.

.. note:: Caching directives are not automatically in scope, see :ref:`Caching Directive Usage` about how to enable them.

Example
-------

.. includecode:: ../code/docs/directives/CachingDirectivesExamplesSpec.scala
   :snippet: cachingProhibited
