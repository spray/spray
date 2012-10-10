Understanding the DSL Structure
===============================

*spray-routings* rather compact route building DSL with its extensive use of function literals can be somewhat tricky,
especially for users without a lot of Scala experience, so in this chapter we are explaining the mechanics in some more
detail.

Assume you have the following route::

    val route: Route = complete("yeah")

This is essentially equivalent to::

    val route: Route = _.complete("yeah")

which is itself the same as::

    val route: Route = { ctx => ctx.complete("yeah") }

which is a function literal. The function defined by the literal is created at the time the "val" statement is reached
but the code inside of the function is not executed until an actual request is injected into the route structure.
This is all probably quite clear.

Not let's look at this slightly more complex structure::

    val route: Route = {
      get {
        complete("yeah")
      }
    }

This is equivalent to::

    val route: Route = {
      val inner = { ctx => ctx.complete("yeah") }
      get.apply(inner)
    }

First a function object is created from the literal. This function is passed to the apply function of the object
named "``get`` directive", which wraps its argument route (the inner route of the ``get`` directive) with some filter
logic and produces the final route.

Now let's look at this code::

    val route: Route = get {
      println("MARK")
      complete("yeah")
    }

This is equivalent to::

    val route: Route = {
      val inner = {
        println("MARK")
        { ctx => ctx.complete("yeah") }
      }
      get.apply(inner)
    }

As you can see from this different representation of the same code the ``println`` statement is executed when the route
val is *created*, not when a request comes in and the route is *executed*! In order to execute the ``println`` at
request processing time it must be *inside* of the leaf-level route function literal::

    val route: Route = get { ctx =>
      println("MARK")
      ctx.complete("yeah")
    }

The mistake of putting custom logic inside of the route structure, but *outside* of a leaf-level route, and expecting
it to be executed at request-handling time, is probably the most frequent error seen by new *spray* users.


Understanding Extractions
-------------------------

In the examples above there are essentially two "areas" of code that are executed at different times:

- code that runs at route construction time, so usually only once
- code that runs at request-handling time, so for every request anew

If a route structure contains extractions there is one more "area" coming into play.
Let's take a look at this example::

    val route: Route = {
      println("MARK 1")
      get {
        println("MARK 2")
        path("abc" / PathElement) { x =>
          println("MARK 3");      //
          { ctx =>                // code "inside"
            println("MARK 4")     // of the
            ctx.complete("yeah")  // extraction
          }                       //
        }
      }
    }

Here we have put logging statements at four different places in our route structure. Let's see, when exactly they
will be executed.

MARK 1 and MARK 2
  From the analysis in the section above you should be able to see that there is no real difference between the "MARK 1"
  and "MARK 2" statements. They are both executed exactly once, when the route is built.

MARK 3
  This statement lies within a function literal of an extraction, but outside of the leaf-level route. It is executed
  when the request is handled, so essentially shortly before the "MARK 4" statement.

MARK 4
  This statement lives inside of the leaf-level route. As such it is executed anew for every request hitting its route.

Why is the "MARK 3" statement executed for every request, even though it doesn't live at the leaf level?
Because it lives "underneath an extraction". All branches of the route structure that lie inside of a function literal
for an extraction can only be created when the extracted values have been determined. Since the value of the
``PathElement`` in the example above can only be known after a request has come in and its path has been parsed the
branch of the route structure "inside" of the extraction can only be built at request-handling time.

So essentially the sequence of events in the example above is as follows:

1. When the ``val route = ...`` declaration is executed the outer route structure is built.
   The "outer route structure" consists of the ``get`` directive and its direct children, in this case only the ``path``
   directive.

2. When a GET request with a matching URI comes in it flows through the outer route structure up until the point the
   ``path`` directive has extracted the value of the ``PathElement`` placeholder.

3. The extraction function literal is executed, with the extracted ``PathElement`` value as argument. This function
   creates the underlying route structure inside of the extraction.

4. After the inner route structure has been created the request is injected into it. So the inner route structure
   underneath an extraction is being "executed" right after its creation.

Since the route structure inside of an extraction is fully dynamic it might look completely different depending on the
value that has been extracted. In order to keep you route structure readable (and thus maintainable) you probably
shouldn't go too crazy with regard to dynamically creating complex route structures depending on specific extraction
values though. However, understanding why it'd be possible is helpful in getting the most out of the *spray-routing*
DSL.


Performance Tuning
------------------

With the understanding of the above sections it should now be possible to discover optimization potential in your route
structures for the (rare!) cases, where route execution performance really turns out to be a significant factor in your
application.

Let's compare two route structures that are fully equivalent with regard to how they respond to requests::

    val routeA =
      path("abc" / PathElement) { x =>
        get {
          complete(responseFor(x))
        }
      }

    val routeB =
      get {
        path("abc" / PathElement) { x =>
          complete(responseFor(x))
        }
      }

The only difference between ``routeA`` and ``routeB`` is the order in which the ``get`` and the ``path`` directive are
nested. ``routeB`` will be a tiny amount faster in responding to requests, because the dynamic part of the route
structure, i.e. the one that is rebuilt anew for every request, is smaller.

A general recommendation could therefore be to "pull up" directives without extractions as far as possible and only
start extracting values at the lower levels of your routing tree. However, in the grand majority of applications we'd
expect the benefits of a cleanly and logically laid out structure to far outweigh potential performance improvements
through a more complex solution that goes out of its way to push down or even avoid extractions for a tiny,
non-perceivable bump in performance.