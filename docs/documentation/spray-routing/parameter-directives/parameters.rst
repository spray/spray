.. _-parameters-:

parameters
==========

The parameters directive filters on the existence of several query parameters and extract their values.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/ParameterDirectives.scala
   :snippet: parameters

Description
-----------
Query parameters can be either extracted as a String or can be converted to another type. The parameter name
can be supplied either as a String or as a Symbol. Parameter extraction can be modified to mark a query parameter
as required or optional or to filter requests where a parameter has a certain value:

``"color"``
    extract value of parameter "color" as ``String``
``"color".?``
    extract optional value of parameter "color" as ``Option[String]``
``"color" ? "red"``
    extract optional value of parameter "color" as ``String`` with default value ``"red"``
``"color" ! "blue"``
    require value of parameter "color" to be ``"blue"`` and extract nothing
``"amount".as[Int]``
    extract value of parameter "amount" as ``Int``, you need a matching ``Deserializer`` in scope for that to work
    (see also :ref:`unmarshalling`)

You can use :ref:`case-class-extraction` to group several extracted values together into a case-class
instance.

Requests missing a required parameter or parameter value will be rejected with an appropriate rejection.

There's also a singular version, :ref:`-parameter-`.

Examples
--------

Required parameter
++++++++++++++++++

.. includecode:: ../code/docs/directives/ParameterDirectivesExamplesSpec.scala
   :snippet: required-1

Optional parameter
++++++++++++++++++

.. includecode:: ../code/docs/directives/ParameterDirectivesExamplesSpec.scala
   :snippet: optional

Optional parameter with default value
+++++++++++++++++++++++++++++++++++++

.. includecode:: ../code/docs/directives/ParameterDirectivesExamplesSpec.scala
   :snippet: optional-with-default

Parameter with required value
+++++++++++++++++++++++++++++

.. includecode:: ../code/docs/directives/ParameterDirectivesExamplesSpec.scala
   :snippet: required-value

Deserialized parameter
++++++++++++++++++++++

.. includecode:: ../code/docs/directives/ParameterDirectivesExamplesSpec.scala
   :snippet: required-value
