.. _-dynamic-:

dynamic
=======

Enforces that the code **constructing the inner route** is run for every request.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/ExecutionDirectives.scala
   :snippet: dynamic

Description
-----------

``dynamic`` is a special directive because, in fact, it doesn't implement ``Directive`` at all. That means you cannot
use it in combination with the usual directive operators.

Use ``dynamicIf`` to run the inner route constructor dynamically depending on a static condition.

Example
-------

.. includecode:: ../code/docs/directives/ExecutionDirectivesExamplesSpec.scala
   :snippet: dynamic-0
