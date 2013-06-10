Timeout Handling
================

*spray-routing* itself does not perform any timeout checking, it relies on the underlying :ref:`spray-can` or
:ref:`spray-servlet` module to watch for request timeouts. Both, the :ref:`spray-can` :ref:`HTTP Server` and
:ref:`spray-servlet`, define a ``timeout-handler`` config setting, which allows you to specify the path of the actor
to send ``spray.http.Timedout`` messages to whenever a request timeout occurs. By default all ``Timedout`` messages
go to same actor that also handles "regular" request, i.e. your service actor.

``Timedout`` is a simple wrapper around ``HttpRequest`` or ``ChunkedRequestStart`` instances:

.. includecode:: /../spray-http/src/main/scala/spray/http/Timedout.scala
   :snippet: source-quote

If a ``Timedout`` messages hits your service actor :ref:`runRoute <runRoute>` unpacks it and feeds the wrapped request,
i.e. the one that timed out, to the ``timeoutRoute`` defined by the the :ref:`HttpService <HttpService>`.
The default implementation looks like this:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/HttpService.scala
   :snippet: timeout-route

If you'd like to customize how your service reacts to request timeouts simply override the ``timeoutRoute`` method.

Alternatively you can also "catch" ``Timedout`` message before they are handled by :ref:`runRoute <runRoute>` and
handle them in any way you want. Here is an example of what this might look like:

.. includecode:: ../code/docs/TimeoutHandlingExamplesSpec.scala
   :snippet: example-1