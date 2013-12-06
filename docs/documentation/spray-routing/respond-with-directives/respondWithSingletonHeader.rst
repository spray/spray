.. _-respondWithSingletonHeader-:

respondWithSingletonHeader
==========================

Adds a given HTTP header to all responses coming back from its inner route only if a header with the same name doesn't
exist yet in the response.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/RespondWithDirectives.scala
   :snippet: respondWithSingletonHeader


Description
-----------

This directive transforms ``HttpResponse`` and ``ChunkedResponseStart`` messages coming back from its inner route by
potentially adding the given ``HttpHeader`` instance to the headers list.
The header is only added if there is no header instance with the same name (case insensitively) already present in the
response. If you'd like to add more than one header you can use the :ref:`-respondWithSingletonHeaders-` directive instead.


Example
-------

.. includecode:: ../code/docs/directives/RespondWithDirectivesExamplesSpec.scala
   :snippet: respondWithSingletonHeader-examples