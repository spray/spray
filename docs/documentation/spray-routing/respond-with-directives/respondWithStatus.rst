.. _-respondWithStatus-:

respondWithStatus
=================

Overrides the status code of all responses coming back from its inner route with the given one.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/RespondWithDirectives.scala
   :snippet: respondWithStatus


Description
-----------

This directive transforms ``HttpResponse`` and ``ChunkedResponseStart`` messages coming back from its inner route by
unconditionally overriding the status code with the given one.


Example
-------

.. includecode:: ../code/docs/directives/RespondWithDirectivesExamplesSpec.scala
   :snippet: respondWithStatus-examples