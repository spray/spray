.. _-onFailure-:

onFailure
=========

"Unwraps" a ``Future[T]`` and runs its inner route when the future has failed
with the future's failure exception as an extraction of type ``Throwable``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/FutureDirectives.scala
   :snippet: onFailure

Description
-----------

The execution of the inner route passed to a onFailure directive is deferred until the given future
has completed with a failure, exposing the reason of failure as a extraction of type ``Throwable``.
If the future succeeds the request is completed using the values marshaller (this directive therefore
requires a marshaller for the future's type to be implicitly available). The future is evaluated
once at route creation time, in case you need to evaluate the future per each request consider wrapping the
onFailure directive with the :ref:`-dynamic-` directive.

It is necessary to bring a ``ExecutionContext`` into implicit scope for this directive to work.


Example
-------

.. includecode:: ../code/docs/directives/FutureDirectivesExamplesSpec.scala
   :snippet: example-3