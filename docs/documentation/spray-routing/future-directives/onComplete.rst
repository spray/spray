.. _-onComplete-:

onComplete
==========

Evaluates its parameter of type ``Future[T]``, and once the ``Future`` has been completed, extracts its
result as a value of type ``Try[T]`` and passes it to the inner route.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/FutureDirectives.scala
   :snippet: onComplete

Description
-----------

The evaluation of the inner route passed to a onComplete directive is deferred until the given future
has completed and provided with a extraction of type ``Try[T]``.

It is necessary to bring a ``ExecutionContext`` into implicit scope for this directive to work.

To handle the ``Failure`` case automatically and only work with the result value, use ``onSuccess``.
To complete with a successful result automatically and just handle the failure result, use ``onFailure``, instead.

Example
-------

.. includecode:: ../code/docs/directives/FutureDirectivesExamplesSpec.scala
   :snippet: example-1
