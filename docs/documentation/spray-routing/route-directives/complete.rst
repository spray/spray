.. _-complete-:

complete
========

Completes the request using the given argument(s).


Signature
---------

::

    def complete[T :ToResponseMarshaller](value: T): StandardRoute
    def complete(response: HttpResponse): StandardRoute
    def complete(status: StatusCode): StandardRoute
    def complete[T :Marshaller](status: StatusCode, value: T): StandardRoute
    def complete[T :Marshaller](status: Int, value: T): StandardRoute
    def complete[T :Marshaller](status: StatusCode, headers: Seq[HttpHeader], value: T): StandardRoute
    def complete[T :Marshaller](status: Int, headers: Seq[HttpHeader], value: T): StandardRoute

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern </blog/2012-12-13-the-magnet-pattern/>`_ for an explanation of magnet-based overloading.


Description
-----------

``complete`` uses the given arguments to construct a ``Route`` which simply calls ``requestContext.complete`` with the
respective ``HttpResponse`` instance. Completing the request will send the response "back up" the route structure where
all logic that wrapping directives have potentially chained into the responder chain is run (see also :ref:`The Responder Chain`).
Once the response hits the top-level ``runRoute`` logic it is sent back to the underlying :ref:`spray-can` or
:ref:`spray-servlet` layer which will trigger the sending of the actual HTTP response message back to the client.


Example
-------

.. includecode:: ../code/docs/directives/RouteDirectivesExamplesSpec.scala
   :snippet: complete-examples