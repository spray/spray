.. _-filter-:

filter
======

A low-level directive that often forms the basis of some higher-level named configuration is the ``filter`` directive,
which has one parameter of type ``f: RequestContext => FilterResult[L]``, whereby ``L`` is a shapeless_ ``HList``.
If you don't know what an ``HList`` is, don't worry. It's quite an easy structure to work with.

.. _shapeless: https://github.com/milessabin/shapeless