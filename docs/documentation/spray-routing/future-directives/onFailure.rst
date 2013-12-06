.. _-onFailure-:

onFailure
=========

Completes the request with the result of the computation given as argument of type ``Future[T]`` by marshalling it
with the implicitly given ``ToResponseMarshaller[T]``. Runs the inner route if the ``Future`` computation fails.

Signature
---------

::

    def onFailure(future: ⇒ Future[T])(implicit m: ToResponseMarshaller[T], ec: ExecutionContext): Directive1[Throwable]

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

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
