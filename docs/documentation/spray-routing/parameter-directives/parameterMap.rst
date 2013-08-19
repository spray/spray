.. _-parameterMap-:

parameterMap
============

Extracts all parameters at once as a ``Map[String, String]`` mapping parameter names to
parameter values.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/ParameterDirectives.scala
   :snippet: parameterSeq

Description
-----------

If a query contains a parameter value several times, the map will contain the last one.

See :ref:`which-parameter-directive` for other
choices.


Example
-------

.. includecode:: ../code/docs/directives/ParameterDirectivesExamplesSpec.scala
   :snippet: parameterSeq
