.. _-validate-:

validate
========

Checks an arbitrary condition and passes control to the inner route if it returns ``true``. Otherwise, rejects the
request with a ``ValidationRejection`` containing the given error message.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: validate


Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: validate-example
