.. _-logResponse-:

logResponse
===========

Logs the response.

Signature
---------

::

    def logResponse(marker: String)(implicit log: LoggingContext): Directive0
    def logResponse(marker: String, level: LogLevel)(implicit log: LoggingContext): Directive0
    def logResponse(show: Any => String)(implicit log: LoggingContext): Directive0
    def logResponse(show: Any => LogEntry)(implicit log: LoggingContext): Directive0
    def logResponse(magnet: LoggingMagnet[Any => Unit])(implicit log: LoggingContext): Directive0

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

See ``logRequest`` for the general description how these directives work. This directive is different
as it requires a ``LoggingMagnet[Any => Unit]``. Instead of just logging ``HttpResponses``, ``logResponse`` is able to
log anything passing through :ref:`The Responder Chain` (which can either be a ``HttpResponsePart`` or a ``Rejected``
message reporting rejections).

Use ``logRequest`` for logging the request, or ``logRequestResponse`` for logging both.

Example
-------

.. includecode:: ../code/docs/directives/DebuggingDirectivesExamplesSpec.scala
   :snippet: logResponse
