.. _-logRequestResponse-:

logRequestResponse
==================

Logs request and response.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/DebuggingDirectives.scala
   :snippet: logRequestResponse

``LoggingMagnet`` definition:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/DebuggingDirectives.scala
   :snippet: logging-magnet

Implicit ``LoggingMagnet`` constructors:[1]_

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/DebuggingDirectives.scala
   :snippet: request-response-magnets

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

This directive is a combination of ``logRequest`` and ``logResponse``. See ``logRequest`` for the general description
how these directives work.

Example
-------

.. includecode:: ../code/docs/directives/DebuggingDirectivesExamplesSpec.scala
   :snippet: logRequestResponse
