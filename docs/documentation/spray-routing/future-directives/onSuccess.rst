.. _-onSuccess-:

onSuccess
=========

Evaluates its parameter of type ``Future[T]``, and once the ``Future`` has been completed successfully,
extracts its result as a value of type ``T`` and passes it to the inner route.

Signature
---------

::

    def onSuccess(future: ⇒ Future[T])(ec: ExecutionContext): Directive1[T]
    def onSuccess(future: ⇒ Future[L <: HList])(ec: ExecutionContext): Directive[L]

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

The execution of the inner route passed to a onSuccess directive is deferred until the given future
has completed successfully, exposing the future's value as a extraction of type ``T``. If the future
fails its failure throwable is bubbled up to the nearest ``ExceptionHandler``.

It is necessary to bring a ``ExecutionContext`` into implicit scope for this directive to work.

To handle the ``Failure`` case manually as well, use ``onComplete``, instead.

Example
-------

.. includecode:: ../code/docs/directives/FutureDirectivesExamplesSpec.scala
   :snippet: example-2
