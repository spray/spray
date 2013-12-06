.. _-failWith-:

failWith
========

Bubbles up the given error through the route structure where it is dealt with by the closest ``handleExceptions``
directive and its ``ExceptionHandler``.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/RouteDirectives.scala
   :snippet: failWith


Description
-----------

``failWith`` explicitly raises an exception that gets bubbled up through the route structure to be picked up by the
nearest ``handleExceptions`` directive. If no ``handleExceptions`` is present above the respective location in the
route structure :ref:`runRoute` will handle the exception and translate it into a corresponding ``HttpResponse`` using
the in-scope ``ExceptionHandler`` (see also the :ref:`Exception Handling` chapter).

There is one notable special case: If the given exception is a ``RejectionError`` exception it is *not* bubbled up,
but rather the wrapped exception is unpacked and "executed". This allows the "tunneling" of a rejection via an
exception.


Example
-------

.. includecode:: ../code/docs/directives/RouteDirectivesExamplesSpec.scala
   :snippet: failwith-examples