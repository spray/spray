.. _Routes:

Routes
======

"Routes" are a central concept in *spray-routing* since all structures you build with the routing DSL are subtypes of
type ``Route``. In *spray-routing* a route is defined like this::

  type Route = RequestContext => Unit

It's a simple alias for a function taking a ``RequestContext`` as parameter.

Contrary to what you might initially expect a route does not return anything. Rather, all response processing
(i.e. everything that needs to be done after the route itself has handled a request) is performed in
"continuation-style" via the ``responder`` of the ``RequestContext``. If you don't know what this means, don't worry.
It'll become clear soon. The key point is that this design has the advantage of being completely non-blocking as well
as actor-friendly since, this way, it's possible to simply send off a ``RequestContext`` to another actor in a
"fire-and-forget" manner, without having to worry about results handling.

Generally when a route receives a request (or rather a ``RequestContext`` for it) it can do one of three things:

- Complete the request by calling ``requestContext.complete(...)``
- Reject the request by calling ``requestContext.reject(...)``
- Ignore the request (i.e. neither complete nor reject it)

The first case is pretty clear, by calling ``complete`` a given response is sent to the client as reaction to the
request. In the second case "reject" means that the route does not want to handle the request. You'll see further down
in the section about route composition what this is good for. The third case is usually an error. If a route does not
do anything with the request it will simply not be acted upon. This means that the client will not receive a response
until the request times out, at which point a ``500 Internal Server Error`` response will be generated.
Therefore your routes should usually end up either completing or rejecting the request.


Constructing Routes
-------------------

Since routes are ordinary functions ``RequestContext => Unit``, the simplest route is::

  ctx => ctx.complete("Response")

or shorter::

  _.complete("Response")

or even shorter (using the :ref:`-complete-` directive)::

  complete("Response")

All these are different ways of defining the same thing, namely a ``Route`` that simply completes all requests with a
static response.

Even though you could write all your application logic as one monolithic function that inspects the ``RequestContext``
and completes it depending on its properties this type of design would be hard to read, maintain and reuse.
Therefore *spray-routing* allows you to construct more complex routes from simpler ones through composition.


Composing Routes
----------------

There are three basic operations we need for building more complex routes from simpler ones:

.. rst-class:: wide

- Route transformation, which delegates processing to another, "inner" route but in the process changes some properties
  of either the incoming request, the outgoing response or both
- Route filtering, which only lets requests satisfying a given filter condition pass and rejects all others
- Route chaining, which tries a second route if a given first one was rejected

The last point is achieved with the simple operator ``~``, which is available to all routes via a "pimp", i.e. an
implicit "extension". The first two points are provided by so-called :ref:`Directives`, of which a large number is
already predefined by *spray-routing* and which you can also easily create yourself.
:ref:`Directives` deliver most of *spray-routings* power and flexibility.


The Routing Tree
----------------

Essentially, when you combine directives and custom routes via nesting and the ``~`` operator, you build a routing
structure that forms a tree. When a request comes in it is injected into this tree at the root and flows down through
all the branches in a depth-first manner until either some node completes it or it is fully rejected.

Consider this schematic example::

  val route =
    a {
      b {
        c {
          ... // route 1
        } ~
        d {
          ... // route 2
        } ~
        ... // route 3
      } ~
      e {
        ... // route 4
      }
    }

Here five directives form a routing tree.

.. rst-class:: wide

- Route 1 will only be reached if directives ``a``, ``b`` and ``c`` all let the request pass through.
- Route 2 will run if ``a`` and ``b`` pass, ``c`` rejects and ``d`` passes.
- Route 3 will run if ``a`` and ``b`` pass, but ``c`` and ``d`` reject.

Route 3 can therefore be seen as a "catch-all" route that only kicks in, if routes chained into preceding positions
reject. This mechanism can make complex filtering logic quite easy to implement: simply put the most
specific cases up front and the most general cases in the back.