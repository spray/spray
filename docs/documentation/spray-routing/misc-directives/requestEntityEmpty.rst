.. _-requestEntityEmpty-:

requestEntityEmpty
==================

A filter that checks if the request entity is empty and only then passes processing to the inner route.
Otherwise, the request is rejected.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: requestEntityEmpty


Description
-----------

The opposite filter is available as ``requestEntityPresent``.


Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: requestEntityEmptyPresent-example
