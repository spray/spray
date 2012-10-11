.. _Rejections:

Rejections
==========

In the chapter about constructing :ref:`Routes` the ``~`` operator was introduced, which connects two routes in a way
that allows a second route to get a go at a request if the first route "rejected" it. The concept of "rejections" is
used by *spray-routing* for maintaining a more functional overall architecture and in order to be able to properly
handle all kinds of error scenarios.

When a filtering directive, like the ``get`` directive, cannot let the request pass through to its inner route because
the filter condition is not satisfied (e.g. because the incoming request is not a GET request) the directive doesn't
immediately complete the request with an error response. Doing so would make it impossible for other routes chained in
after the failing filter to get a chance to handle the request.
Rather, failing filters "reject" the request in the same way as by explicitly calling ``requestContext.reject(...)``.

After having been rejected by a route the request will continue to flow through the routing structure and possibly find
another route that can complete it. If there are more rejections all of them will be picked up and collected.

If the request cannot be completed by (a branch of) the route structure an enclosing ``handleRejections`` directive
can be used to convert a set of rejections into an ``HttpResponse`` (which, in most cases, will be an error response).
The ``runRoute`` wrapper defined the ``HttpService`` trait internally wraps its argument route with the
``handleRejections`` directive in order to "catch" and handle any rejection.


RejectionHandler
----------------

The ``handleRejections`` directive delegates the actual job of converting a list of rejections to its argument, a
RejectionHandler__, which is defined like this::

    trait RejectionHandler extends PartialFunction[List[Rejection], HttpResponse]

__ https://github.com/spray/spray/blob/master/spray-routing/src/main/scala/cc/spray/routing/RejectionHandler.scala

Since a ``RejectionHandler`` is a partial function it can choose, which rejections it would like to handle and
which not. Unhandled rejections will simply continue to flow through the route structure. The top-most
``RejectionHandler`` applied by the ``HttpService.runRoute`` wrapper will handle *all* rejections that reach it.

So, if you'd like to customize the way certain rejections are handled simply bring a custom ``RejectionHandler`` into
implicit scope of the ``runRoute`` wrapper or pass it to an explicit ``handleRejections`` directive that you
have put somewhere into your route structure.

Here is an example:

.. includecode:: ../../code/docs/RejectionHandlerExamplesSpec.scala
   :snippet: example-1


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