.. _-requestEntityPresent-:

requestEntityPresent
====================

A simple filter that checks if the request entity is present and only then passes processing to the inner route.
Otherwise, the request is rejected.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: requestEntityPresent


Description
-----------

The opposite filter is available as ``requestEntityEmpty``.


Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: requestEntityEmptyPresent-example
