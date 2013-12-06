.. _-onComplete-:

onComplete
==========

Evaluates its parameter of type ``Future[T]``, and once the ``Future`` has been completed, extracts its
result as a value of type ``Try[T]`` and passes it to the inner route.

Signature
---------

::

    def onComplete[T](future: ⇒ Future[T])(implicit ec: ExecutionContext): Directive1[Try[T]]

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

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
