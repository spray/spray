.. _-provide-:

provide
=======

Provides a constant value to the inner route.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/BasicDirectives.scala
   :snippet: provide

Description
-----------

The `provide` directive is used as a building block for :ref:`Custom Directives` to provide a single value to the
inner route. To provide several values  use the :ref:`-hprovide-` directive.

See :ref:`ProvideDirectives` for an overview of similar directives.

Example
-------

.. includecode:: ../code/docs/directives/BasicDirectivesExamplesSpec.scala
   :snippet: 0provide
