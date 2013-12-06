.. _-logRequestResponse-:

logRequestResponse
==================

Logs request and response.

Signature
---------

::

    def logRequestResponse(marker: String)(implicit log: LoggingContext): Directive0
    def logRequestResponse(marker: String, level: LogLevel)(implicit log: LoggingContext): Directive0
    def logRequestResponse(show: HttpRequest ⇒ HttpResponsePart ⇒ Option[LogEntry])
                          (implicit log: LoggingContext): Directive0
    def logRequestResponse(show: HttpRequest ⇒ Any ⇒ Option[LogEntry])(implicit log: LoggingContext): Directive0

The signature shown is simplified, the real signature uses magnets. [1]_

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
