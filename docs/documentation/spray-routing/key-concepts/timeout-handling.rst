Timeout Handling
================

*spray-routing* itself does not perform any timeout checking, it relies on the underlying :ref:`spray-can` or
:ref:`spray-servlet` module to watch for request timeouts. Both, the :ref:`spray-can` :ref:`HttpServer` and
:ref:`spray-servlet`, define a ``timeout-handler`` config setting, which allows you to specify the path of the actor
to send ``spray.http.Timeout`` messages to whenever a request timeout occurs. By default all ``Timeout`` messages
go to same actor that also handles "regular" request, i.e. your service actor.

``Timeout`` is a simple wrapper around ``HttpRequest`` or ``HttpResponse`` instances (because it is used on the
client-side as well as on the server-side):

.. includecode:: /../spray-http/src/main/scala/spray/http/Timeout.scala
   :snippet: source-quote

If a ``Timeout`` messages hits your service actor :ref:`runRoute <runRoute>` unpacks it and feeds the wrapped request,
i.e. the one that timed out, to the ``timeoutRoute`` defined by the the :ref:`HttpService <HttpService>`.
The default implementation looks like this:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/HttpService.scala
   :snippet: timeout-route

If you'd like to customize how your service reacts to request timeouts, simply override the ``timeoutRoute`` method.

Alternatively you can also "catch" ``Timeout`` message before they are handled by :ref:`runRoute <runRoute>` and
handle them in any way you want. Here is an example of what this might look like:

.. includecode:: ../code/docs/TimeoutHandlingExamplesSpec.scala
   :snippet: example-1