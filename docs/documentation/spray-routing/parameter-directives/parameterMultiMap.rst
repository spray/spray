.. _-parameterMultiMap-:

parameterMultiMap
=================

Extracts all parameters at once as a multi-map of type ``Map[String, List[String]`` mapping
a parameter name to a list of all its values.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/ParameterDirectives.scala
   :snippet: parameterMultiMap

Description
-----------

This directive can be used if parameters can occur several times. The order of values is
not specified.

See :ref:`which-parameter-directive` for other choices.

Example
-------

.. includecode:: ../code/docs/directives/ParameterDirectivesExamplesSpec.scala
   :snippet: parameterMultiMap
