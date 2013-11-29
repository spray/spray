.. _-hextract-:

hextract
========

Calculates an HList of values from the request context and provides them to the inner route.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/BasicDirectives.scala
   :snippet: hextract

Description
-----------

The ``hextract`` directive is used as a building block for :ref:`Custom Directives` to extract data from the
``RequestContext`` and provide it to the inner route. To extract just one value use the :ref:`-extract-` directive. To
provide a constant value independent of the ``RequestContext`` use the :ref:`-hprovide-` directive instead.

See :ref:`ProvideDirectives` for an overview of similar directives.


Example
-------

.. includecode:: ../code/docs/directives/BasicDirectivesExamplesSpec.scala
   :snippet: hextract
