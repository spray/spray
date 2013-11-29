.. _-onFailure-:

onFailure
=========

Completes the request with the result of the computation given as argument of type ``Future[T]`` by marshalling it
with the implicitly given ``ToResponseMarshaller[T]``. Runs the inner route if the ``Future`` computation fails.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/FutureDirectives.scala
   :snippet: onFailure

Description
-----------

If the future succeeds the request is completed using the values marshaller (this directive therefore
requires a marshaller for the future's type to be implicitly available). The execution of the inner
route passed to a onFailure directive is deferred until the given future has completed with a failure,
exposing the reason of failure as a extraction of type ``Throwable``.

It is necessary to bring a ``ExecutionContext`` into implicit scope for this directive to work.

To handle the successful case manually as well, use the ``onComplete`` directive, instead.

Example
-------

.. includecode:: ../code/docs/directives/FutureDirectivesExamplesSpec.scala
   :snippet: example-3
