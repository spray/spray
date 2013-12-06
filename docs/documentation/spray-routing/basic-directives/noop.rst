.. _-noop-:

noop
====

A directive that passes the request unchanged to its inner route.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/BasicDirectives.scala
   :snippet: noop

Description
-----------

The directive is usually used as a "neutral element" when combining directives generically.


Example
-------

.. includecode:: ../code/docs/directives/BasicDirectivesExamplesSpec.scala
   :snippet: noop
