.. _-logRequest-:

logRequest
==========

Logs the request.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/DebuggingDirectives.scala
   :snippet: logRequest

``LoggingMagnet`` definition:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/DebuggingDirectives.scala
   :snippet: logging-magnet

Implicit ``LoggingMagnet`` constructors:[1]_

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/DebuggingDirectives.scala
   :snippet: message-magnets

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

Logs the request using the supplied ``LoggingMagnet[HttpRequest => Unit]``.  This ``LoggingMagnet`` is a wrapped
function ``HttpRequest => Unit`` that can be implicitly created from the different constructors shown above. These
constructors build a ``LoggingMagnet`` from these components:

  * A marker to prefix each log message with.
  * A log level.
  * A ``show`` function that calculates a string representation for a request.
  * An implicit ``LoggingContext`` that is used to emit the log message.
  * A function that creates a ``LogEntry`` which is a combination of the elements above.

It is also possible to use any other function ``HttpRequest => Unit`` for logging by wrapping it with ``LoggingMagnet``.
See the examples for ways to use the ``logRequest`` directive.

Use ``logResponse`` for logging the response, or ``logRequestResponse`` for logging both.

Example
-------

.. includecode:: ../code/docs/directives/DebuggingDirectivesExamplesSpec.scala
   :snippet: logRequest-0
