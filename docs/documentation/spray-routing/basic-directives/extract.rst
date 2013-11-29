.. _-extract-:

extract
=======

Calculates a value from the request context and provides the value to the inner route.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/BasicDirectives.scala
   :snippet: extract

Description
-----------

The ``extract`` directive is used as a building block for :ref:`Custom Directives` to extract data from the
``RequestContext`` and provide it to the inner route. It is a special case for extracting one value of the more
general :ref:`-hextract-` directive that can be used to extract more than one value.

See :ref:`ProvideDirectives` for an overview of similar directives.

Example
-------

.. includecode:: ../code/docs/directives/BasicDirectivesExamplesSpec.scala
   :snippet: 0extract
