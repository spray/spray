.. _-onSuccess-:

onSuccess
=========

"Unwraps" a ``Future[T]`` and run its inner route after future completion with the future's
value as an extraction of type ``T``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/FutureDirectives.scala
   :snippet: onSuccess

Description
-----------

The execution of the inner route passed to a onSuccess directive is deferred until the given future
has completed successfully, exposing the future's value as a extraction of type ``T``. If the future
fails its failure throwable is bubbled up to the nearest ``ExceptionHandler``. The future is evaluated
once at route creation time, ideally in order to prepare some costly resource necessary to evaluate
the inner route. In case you need to evaluate the future per each request, consider wrapping the
onSuccess directive with the :ref:`-dynamic-` directive.

It is necessary to bring a ``ExecutionContext`` into implicit scope for this directive to work.


Example
-------

.. includecode:: ../code/docs/directives/FutureDirectivesExamplesSpec.scala
   :snippet: example-2