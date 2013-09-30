.. _-onComplete-:

onComplete
==========

"Unwraps" a ``Future[T]`` and run its inner route after future completion with the future's
value as an extraction of type ``Try[T]``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/FutureDirectives.scala
   :snippet: onComplete

Description
-----------

The evaluation of the inner route passed to a onComplete directive is deferred until the given future
has completed and provided with a extraction of type ``Try[T]``.

It is necessary to bring a ``ExecutionContext`` into implicit scope for this directive to work.


Example
-------

.. includecode:: ../code/docs/directives/FutureDirectivesExamplesSpec.scala
   :snippet: example-1