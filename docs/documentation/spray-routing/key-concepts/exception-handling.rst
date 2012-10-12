.. _Exception Handling:

Exception Handling
==================

Exceptions thrown during route execution bubble up throw the route structure up to the next enclosing
``handleExceptions`` directive, the main ``runRoute`` wrapper or the ``receive`` function of a ``detachTo`` actor.

Similarly to the way that :ref:`Rejections` are handled the ``handleExceptions`` directive delegates the actual job of
converting a list of rejections to its argument, an ExceptionHandler__, which is defined like this::

    trait ExceptionHandler extends PartialFunction[Throwable, LoggingAdapter => Route]

__ https://github.com/spray/spray/blob/master/spray-routing/src/main/scala/spray/routing/ExceptionHandler.scala

The ``runRoute`` wrapper defined in the ``HttpService`` trait does the same but gets its ``ExceptionHandler`` instance
implicitly.

Since an ``ExceptionHandler`` is a partial function it can choose, which exceptions it would like to handle and
which not. Unhandled exceptions will simply continue to bubble up in the route structure. The top-most
``ExceptionHandler`` applied by the ``HttpService.runRoute`` wrapper will handle *all* exceptions that reach it.

So, if you'd like to customize the way certain exceptions are handled simply bring a custom ``ExceptionHandler`` into
implicit scope of the ``runRoute`` wrapper or pass it to an explicit ``handleExceptions`` directive that you
have put somewhere into your route structure.

Here is an example:

.. includecode:: ../code/docs/ExceptionHandlerExamplesSpec.scala
   :snippet: example-1