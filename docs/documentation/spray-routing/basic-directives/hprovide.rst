.. _-hprovide-:

hprovide
========

Provides an HList of values to the inner route.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/BasicDirectives.scala
   :snippet: hprovide

Description
-----------

The ``hprovide`` directive is used as a building block for :ref:`Custom Directives` to provide data to the inner route.
To provide just one value use the :ref:`-provide-` directive. If you want to provide values calculated from the
``RequestContext`` use the :ref:`-hextract-` directive instead.

See :ref:`ProvideDirectives` for an overview of similar directives.


Example
-------

.. includecode:: ../code/docs/directives/BasicDirectivesExamplesSpec.scala
   :snippet: hprovide
