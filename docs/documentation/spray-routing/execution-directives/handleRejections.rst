.. _-handleRejections-:

handleRejections
================

Handles rejections produced by the inner route and handles them using the specified ``RejectionHandler``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/ExecutionDirectives.scala
   :snippet: handleRejections

Description
-----------

Using this directive is an alternative to using a global implicily defined ``RejectionHandler`` that
applies to the complete route.

See :ref:`Rejections` for general information about options for handling rejections.

Example
-------

.. includecode:: ../code/docs/directives/ExecutionDirectivesExamplesSpec.scala
   :snippet: handleRejections
