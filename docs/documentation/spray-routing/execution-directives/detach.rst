.. _-detach-:

detach
========

Executes the inner route inside a future.

Signature
---------

::

    def detach()(implicit ec: ExecutionContext): Directive0
    def detach()(implicit refFactory: ActorRefFactory): Directive0
    def detach(ec: ExecutionContext): Directive0

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

This directive needs either an implicit ``ExecutionContext`` (``detach()``) or an explicit one (``detach(ec)``).

.. caution:: It is a common mistake to access actor state from code run inside a future that is created inside an actor by
   accidentally accessing instance methods or variables of the actor that are available in the scope. This also applies
   to the ``detach`` directive if a route is run inside an actor which is the usual case.
   Make sure not to access any actor state from inside the ``detach`` block directly or indirectly.

   A lesser known fact is that the current semantics of executing :ref:`The Routing Tree` encompasses that
   every route that rejects a request also runs the alternative routes chained with ``~``. This means that when a route
   is rejected out of a ``detach`` block, also all the alternatives tried afterwards are then run out of the future
   originally created for running the ``detach`` block and not any more from the original (actor) context
   starting the request processing. To avoid that use ``detach`` only at places inside the routing tree
   where no rejections are expected.

Example
-------

.. includecode:: ../code/docs/directives/ExecutionDirectivesExamplesSpec.scala
   :snippet: detach-0

This example demonstrates the effect of the note above:

.. includecode:: ../code/docs/directives/ExecutionDirectivesExamplesSpec.scala
   :snippet: detach-1
