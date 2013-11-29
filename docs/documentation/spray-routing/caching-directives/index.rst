.. _CachingDirectives:

CachingDirectives
=================

.. toctree::
   :maxdepth: 1

   alwaysCache
   cache
   cachingProhibited

.. _Caching Directive Usage:

Usage
-----

To use the caching directives you need to add a dependency to the :ref:`spray-caching` module.
Caching directives are not automatically in scope using the ``HttpService`` or ``Directives`` trait
but must either be brought into scope by extending from ``CachingDirectives`` or by using
``import CachingDirectives._``.
