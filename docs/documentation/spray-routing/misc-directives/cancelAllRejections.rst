.. _-cancelAllRejections-:

cancelAllRejections
===================

Cancels all rejections created by the inner route for which the condition argument function returns ``true``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: cancelAllRejections


Description
-----------

Use the ``cancelRejection`` to cancel a specific rejection instance.


Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: cancelAllRejections
