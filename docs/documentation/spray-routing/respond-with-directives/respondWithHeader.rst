.. _-respondWithHeader-:

respondWithHeader
=================

Adds a given HTTP header to all responses coming back from its inner route.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/RespondWithDirectives.scala
   :snippet: respondWithHeader


Description
-----------

This directive transforms ``HttpResponse`` and ``ChunkedResponseStart`` messages coming back from its inner route by
adding the given ``HttpHeader`` instance to the headers list.
If you'd like to add more than one header you can use the :ref:`-respondWithHeaders-` directive instead.


Example
-------

.. includecode:: ../code/docs/directives/RespondWithDirectivesExamplesSpec.scala
   :snippet: respondWithHeader-examples