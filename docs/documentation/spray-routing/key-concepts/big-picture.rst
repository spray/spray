Big Picture
===========

The *spray-can* :ref:`HttpServer` and the :ref:`spray-servlet` connector servlet both provide an actor-level interface
that allows your application to respond to incoming HTTP requests by simply replying with an ``HttpResponse``:

.. includecode:: ../code/docs/HttpServiceExamplesSpec.scala
   :snippet: example-3

While it'd be perfectly possible to define a complete REST API service purely by pattern-matching against the incoming
``HttpRequest`` (maybe with the help of a few extractors in the way of `Unfiltered`_) this approach becomes somewhat
unwieldy for larger services due to the amount of syntax "ceremony" required. Also, it doesn't help in keeping your
service definition as DRY_ as you might like.

As an alternative *spray-routing* provides a flexible DSL for expressing your service behavior as a structure of
composable elements (called :ref:`Directives`) in a concise and readable way. At the top-level, as the result of the
``runRoute`` wrapper, the "route structure" produces an ``Actor.Receive`` partial function that can be directly supplied
to your service actor.
The service definition from above for example, written using the routing DSL, would look like this:

.. includecode:: ../code/docs/HttpServiceExamplesSpec.scala
   :snippet: example-4

This very short example is certainly not the best for illustrating the savings in "ceremony" and improvements in
conciseness and readability that *spray-routing* promises. The :ref:`Longer Example` might do a better job in this
regard.

For learning how to work with the *spray-routing* DSL you should first understand the concept of :ref:`Routes`.

.. _HttpService:

The *HttpService*
-----------------

*spray-routing* makes all relevant parts of the routing DSL available through the HttpService__ trait, which you can
mix into your service actor or route test. The ``HttpService`` trait defines only one abstract member::

    def actorRefFactory: ActorRefFactory

which connects the routing DSL to your actor hierarchy. In order to have access to all ``HttpService`` members in your
service actor you can either mix in the ``HttpService`` trait and add this line to your actor class::

    def actorRefFactory = context

or, instead of the ``HttpService`` itself, mix in the ``HttpServiceActor`` trait, which already defines the connecting
``def actorRefFactory = context`` for you.


.. _runRoute:

The *runRoute* Wrapper
----------------------

Apart from all the :ref:`predefined directives <Predefined Directives>` the ``HttpService`` provides one important
thing, the ``runRoute`` wrapper. This method connects your route structure to the enclosing actor by constructing an
``Actor.Receive`` partial function that you can directly use as the "behavior" function of your actor:

.. includecode:: ../code/docs/HttpServiceExamplesSpec.scala
   :snippet: example-4

__  https://github.com/spray/spray/blob/master/spray-routing/src/main/scala/spray/routing/HttpService.scala
.. _Unfiltered: http://unfiltered.databinder.net/
.. _DRY: http://en.wikipedia.org/wiki/DRY