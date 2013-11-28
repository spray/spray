.. _-dynamicIf-:

dynamicIf
=========

Enforces that the code **constructing the inner route** is run for every request if the condition is true.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/ExecutionDirectives.scala
   :snippet: dynamicIf

Description
-----------

The effect of ``dynamicIf(true)`` is the same as for ``dynamic``. The effect of ``dynamicIf(false)`` is the same as
just the nested block.

``dynamicIf`` is a special directive because, in fact, it doesn't implement ``Directive`` at all. That means you cannot
use it in combination with the usual directive operators.

Use ``dynamic`` to run the inner route constructor dynamically unconditionally.

Example
-------

.. includecode:: ../code/docs/directives/ExecutionDirectivesExamplesSpec.scala
   :snippet: dynamicIf
