:author: Mathias
:tags: scala, pattern
:index-paragraphs: 1
:show-post-structure: yes

The Magnet Pattern
==================

In this post I'd like to shine some light on one particular implementation technique that is used extensively under the
hood of *sprays* :ref:`routing DSL <spray-routing>` and which has turned out as a very valuable tool in our "pattern
toolbox". It solves certain kinds of problems in Scala API design and DSL development, especially (but not only) with
regard to overloaded methods.
We call it the *magnet pattern* and, hopefully, by the time you've finished reading this post this name will make sense
to you.

Before diving into the details I'd like to make clear that we are certainly not the first ones to use this pattern and
by no means claim its original "inventorship". However, we find it interesting and valuable enough to give it a proper
name and dedicate a somewhat lengthy blog post to its description. (In the process we'll touch on quite a few
interesting edge cases of the current Scala language implementation, so I hope you'll learn something even if you
consider yourself a somewhat seasoned Scala developer.)

*(Update 2012-12-17: The following two paragraphs were added.)*

There have been some questions as to how the technique presented here differs from type classes, which are an
increasingly common and widely used mechanism in Scala (see `this paper`_ for some good background material).
The short answer is: There is no real difference. Rather, we see magnets as a specific application of type classes,
which are a broader, more general concept. Among other things type classes can be used for type-level computation or
advanced generic programming as in shapeless_. Most people probably first come to know them as a solution
for associating logic with types in a way that allows for very loose coupling and *retroactive extension*.
For example, `spray-json`_ uses type classes to attach JSON (de)serialization logic to types "from the outside".

As shown in this post type classes can also be used to solve certain issues with regard to method overloading in Scala.
Our intent is not to "rebrand" type classes, but rather to describe a way of using them for a specific purpose.
In the case of method overloading we see value in labelling the combination of purpose and implementation technique
with a dedicated name. With some kind of naming convention it's easier for someone reading a piece of code to derive
intent and more quickly understand the purpose of a particular construct. This, describing a particular use case for
type classes along with a proposal for naming the things involved, is what this post is all about.

.. _this paper: http://ropas.snu.ac.kr/~bruno/papers/TypeClasses.pdf
.. _shapeless: https://github.com/milessabin/shapeless
.. _spray-json: https://github.com/spray/spray-json


The Problem
-----------

    There are only two hard things in Computer Science:
    cache invalidation, naming things and off-by-1 errors.

    -- Phil Karlton (slightly adapted)

When you design a Scala API, be it in the context of a DSL or not, especially the "naming things" part can be one of
the main challenges. Ideally the names you pick nicely capture the respective concepts of the domain you are modelling.
If there are several ways the *same* domain concept can be applied, as is often the case, the representations of these
alternatives in your API should also receive the same name. In your code this usually manifests itself as what is
commonly called "method overloading".

For example, in the domain of HTTP servers there is the concept of "finalizing" the processing of an HTTP request by
sending some kind of response to the client. In *sprays* :ref:`routing DSL <spray-routing>` this concept goes by
the name "complete". Thereby several ways of request "completion" are supported. Currently you can "complete" a
request with either one of these seven sets of things:

- just a status code
- a custom object (that is to be marshalled as the response entity)
- a status code and a custom object
- a status code, a list of HTTP response headers and a custom object
- an ``HttpResponse`` object
- a ``Future[HttpResponse]``
- a ``Future[StatusCode]``

Since all of these are just different ways of achieving the same thing, namely "completing" the request, they should
all be accessible via the same name in our DSL, namely ``complete``. We do not want to clutter our DSL with seven
different names like ``completeWithStatus``, ``completeWithStatusAndObject`` and so on. Luckily, Scala allows for
method overloading, so we could try to model our seven different completion alternatives with these method overloads::

  def complete(status: StatusCode)
  def complete[T :Marshaller](obj: T)
  def complete[T :Marshaller](status: StatusCode, obj: T)
  def complete[T :Marshaller](status: StatusCode, headers: List[HttpHeader], obj: T)
  def complete(response: HttpResponse)
  def complete(future: Future[HttpResponse])
  def complete(future: Future[StatusCode])

Unfortunately though, method overloading in Scala comes with (at least) the following problems and inconveniences:

1. "Collisions" caused by type erasure
2. No lifting into a function (of all overloads at the same time)
3. Unavailability in package objects (before Scala 2.10)
4. Code duplication in case of many similar overloads
5. Limitations with regard to parameter defaults
6. Limitations with regard to type inference on arguments

The magnet pattern can solve all but the last two of these issues and we are going to discuss them in detail in a
moment, but first let's understand what the magnet pattern actually *is*.


Method Overloading Reloaded
---------------------------

The magnet pattern is an alternative approach to method overloading. Rather than defining several identically named
methods with different parameter lists you define only one method with only one parameter. This parameter is called
the *magnet*. Its type is the *magnet type*, a dedicated type constructed purely as the target of a number of implicit
conversions defined in the magnets companion object, which are called the *magnet branches* and which model the
various "overloads".


Show me the Code
~~~~~~~~~~~~~~~~

Let's go back to our "complete" example from above to understand what this means. However, in order to focus on the key
elements we are going to look only at the following three slightly adapted overloads::

  def complete[T :Marshaller](status: StatusCode, obj: T): Unit
  def complete(future: Future[HttpResponse]): Int
  def complete(future: Future[StatusCode]): Int

For the sake of the example the return types are different from the actual *spray-routing* implementation but they serve
well for illustrating the concepts. In order to model these three ``complete`` overloads with the magnet pattern we
replace them with this single method definition::

  def complete(magnet: CompletionMagnet): magnet.Result = magnet()

The ``CompletionMagnet`` is the following simple trait::

  sealed trait CompletionMagnet {
    type Result
    def apply(): Result
  }

The magnet branches are were the actual logic lives. They represent the different overload implementations we had
before and are defined as implicit conversions to ``CompletionMagnet`` instances in the companion object::

  object CompletionMagnet {
    implicit def fromStatusObject[T :Marshaller](tuple: (StatusCode, T)) =
      new CompletionMagnet {
        type Result = Unit
        def apply(): Result = ... // implementation using (StatusCode, T) tuple
      }
    implicit def fromHttpResponseFuture(future: Future[HttpResponse]) =
      new CompletionMagnet {
        type Result = Int
        def apply(): Result = ... // implementation using future
      }
    implicit def fromStatusCodeFuture(future: Future[StatusCode]) =
      new CompletionMagnet {
        type Result = Int
        def apply(): Result = ... // implementation using future
      }
  }

That's all we need in order to model method overloading with a magnet. All of the following calls will execute the
logic in the respective magnet branch, just as if we had defined them with "regular" overloads::

  complete(StatusCodes.OK, "All fine") // returns Unit
  complete(someHttpResponseFuture) // returns Int
  complete(someStatusCodeFuture) // returns Int


How does it work?
~~~~~~~~~~~~~~~~~

The ``magnet`` parameter on the single ``complete`` method we defined serves only as the
"center of gravity" towards which the different magnet branches define implicit conversions. If you call ``complete``
with an argument that is not a ``CompletionMagnet`` instance itself, as is usually the case, the compiler looks for
an implicit conversion that it can use to turn the argument you specified into an ``CompletionMagnet``, so that your
call becomes legal. Since implicit conversions defined in the companion object of any involved type are automatically
in scope the compiler can "see" and select the matching magnet branch (if there is one) and we are set.

What is interesting is that this approach also works for "overloads" with more than one parameter just as well as
different return types. If you call ``complete`` with several arguments the compiler looks for an implicit conversion
that can produce a magnet instance from *a tuple* wrapping all arguments. This way overloads with up to 22 parameters
(the maximum arity of tuples in scala) can be supported.

If the overloads differ in their return types, as in our example above, we can resort to *dependent method types* to
model them. Dependent method types are available in Scala 2.9 as an experimental feature and thus need to be
:ref:`explicitly enabled <spray-routing-installation>`. Even though they can be used for building powerful constructs
there is nothing particularly dangerous or magical about them, so as of Scala 2.10 dependent method types are always
enabled and do not even require a `SIP-18-style language import`__. What they allow you to do is to specify the return
type of a method as "a function of" the method parameters, which is exactly what we are doing in the example above.

__ http://docs.scala-lang.org/sips/pending/modularizing-language-features.html


Implementation Notes
~~~~~~~~~~~~~~~~~~~~

.. rst-class:: wide

- If all overloads have the same return type there is no need for a ``type`` member on the magnet type. The central
  method with the magnet parameter (``def complete`` in the example above) can then simply have the return type directly
  in its signature.

- If the magnet branch implementations share common logic you can of course factor it out, e.g. into private helpers
  on the magnet companion object. Another option would be to pull it up into the central method itself
  (``def complete`` in the example above) and have the magnet only contribute the parts that differ between the
  overloads.

- Since it'll never be called from the outside the name of the abstract method in the magnet trait doesn't really
  matter. You might even want to mark it ``private[module_name]``. Also the names of the implicit conversions on the
  magnet companion don't really matter. As you can see above we call them ``from<source-type>`` by convention.


Benefits
--------

So, what does this alternative approach to method overloading give us? As it turns out it solves most of the problems
with method overloading that we listed before. Of course, it also comes with a couple of drawbacks of its own, but first
let's look into the advantages a bit deeper.


No Erasure-induced Collisions
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Collisions caused by type-erasure probably pose the most severe problem of "traditional" method overloading on the JVM,
since there is no clean work-around. It actually prevents us from implementing our ``complete`` overloads in the usual
fashion, as can be seen by the following error the Scala compiler produces when we try to::

   [error] ...: double definition:
   [error] method complete:(future: scala.concurrent.Future[spray.http.StatusCode])Int and
   [error] method complete:(future: scala.concurrent.Future[spray.http.HttpResponse])Int
   [error] have same type after erasure: (future: concurrent.Future)Int
   [error]   def complete(future: Future[StatusCode]) = { ...
   [error]       ^
   [error] one error found

The compiler is telling us that the last two of our overloads are a "double definition" because of type erasure.
In order to understand what's going on we have to take a quick look at how methods are represented by the JVM.
The JVM supports generics through `type erasure`_ (rather than `type reification`_ as Microsofts CLR_ does, check out
`this article`__ for more info on the difference). This means that all parameter types on generic types (in Java speak)
are erased and non-existent on the JVM level. To the JVM our two overloads::

  def complete(future: Future[HttpResponse]): Unit
  def complete(future: Future[StatusCode]): Unit

both look like this::

  def complete(future: Future): Unit

Since the compiler cannot produce two different implementation for the same method it has to give up.

This erasure-induced limitation to method overloading is not specific to Scala. Java and other JVM-based languages
suffer from it as well. Theoretically we could hack our way around it by introducing "fake" return types for the
colliding methods (since the return type is part of the method signature and therefore sufficient to discriminate
between overloads), but in Scala we don't have to. With overloading via magnets we can remove the need to supply two
different implementations for the same (as seen by the JVM) method and nicely overcome the "collision problem" without
having to compromise our API on the type level.

__ http://www.jprl.com/Blog/archive/development/2007/Aug-31.html
.. _type erasure: http://en.wikipedia.org/wiki/Type_erasure
.. _type reification: http://en.wikipedia.org/wiki/Reification_(computer_science)
.. _CLR: http://en.wikipedia.org/wiki/Common_Language_Runtime


Full Function-Lifting
~~~~~~~~~~~~~~~~~~~~~

Scala supports a nice and easy notation for *lifting* a method into a function. Just follow the method name (without
arguments) with a ``_`` as shown in this example::

  scala> def twice(i: Int) = (i * 2).toString
  twice: (i: Int)java.lang.String

  scala> twice _
  res0: Int => java.lang.String = <function1>

Now, if we overload the method like this::

  def twice(i: Int) = (i * 2).toString
  def twice(d: Double) = (d * 2).toString

it'd be nice if we could still simply say ``twice _`` and somehow lift both overloads at once, so that later on we
could call the lifted function with either an ``Int`` or a ``Double``. Unfortunately this is not supported, you have to
decide at the "lifting point", which overload to lift and you can only lift one.

With magnets this lifting of all overloads at once is no problem. In this case the type of ``twice _`` is
``TwiceMagnet => String`` and the "overloadedness" is retained. Only at the point where the lifted function is actually
applied do you have to decide, which overload to choose. Just as in the unlifted case the compiler will supply the
required implicit conversions at the call site.

Unfortunately this type of lifting only works when all overloads have the same return type and thus no dependent method
types are required. For example, if we try to lift our ``complete`` overload from above with ``complete _`` the compiler
will produce the following error::

    error: method with dependent type (magnet: CompletionMagnet)magnet.Result
           cannot be converted to function value
           complete _
           ^


Package Object Support
~~~~~~~~~~~~~~~~~~~~~~

Due to a `long-standing Scala bug`__ that was just recently fixed method overloading in package objects is not supported
with any Scala version before 2.10. If you are searching for a solution for Scala 2.9 or earlier magnets might present
a nice solution.

__ https://issues.scala-lang.org/browse/SI-1987


DRYness for many similar Overloads
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Sometimes DSLs can require the definition of a larger number of very similar method overloads, which reduces DRYness and
generally feels ugly. For example in *spray* 0.9 the ``parameters`` directive, which allows you to define the extraction
of one or more request query parameters, was `defined like this`__::

  def parameters[A](a: PM[A]): SprayRoute1[A] =
    parameter(a)

  def parameters[A, B](a: PM[A], b: PM[B]): SprayRoute2[A, B] =
    parameter(a) & parameter(b)

  def parameters[A, B, C](a: PM[A], b: PM[B], c: PM[C]): SprayRoute3[A, B, C] =
    parameters(a, b) & parameter(c)

  def parameters[A, B, C, D](a: PM[A], b: PM[B], c: PM[C], d: PM[D]): SprayRoute4[A, B, C, D] =
    parameters(a, b, c) & parameter(d)

  ...

Ideally, *spray* would have supported an arbitrary number of parameters like this but due to the duplication required
we only defined nine. After we switched the implementation of the ``parameters`` directive to a combination of magnets
and `shapeless' HLists`_ we can now support up to 22 parameters without any duplication.

The details of how exactly `this is implemented`__ in :ref:`spray-routing` are beyond the scope of this article, but in
essence the solution looks like this: We define a single magnet branch for all tuples at once by making use of
*shapeless'* support for automatically converting tuples to HLists. Since *shapeless* allows us to easily fold over
HLists we can reduce the problem to a binary poly-function that specifies how two parameters are to be combined.
This is pretty much as DRY as it gets.

__ https://github.com/spray/spray/blob/a69a8aefcd2826680b1b302192d6658524fcb4c3/spray-server/src/main/scala/cc/spray/directives/ParameterDirectives.scala
__ https://github.com/spray/spray/blob/master/spray-routing/src/main/scala/spray/routing/directives/ParameterDirectives.scala
.. _shapeless' HLists: https://github.com/milessabin/shapeless


Removal of implicit Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

*sprays* routing DSL tries to provide a way for API definition that is both highly concise and highly readable. As such
it relies heavily on the one method in Scala that beats all others with regard to brevity: ``apply``. If an object
has only one clearly defined behavior or if there is a central one, which clearly outrivals all others in terms of
importance, modeling this behavior as an ``apply`` method is the natural choice. Unfortunately, implicit parameter lists
and ``apply`` occupy "the same namespace", which can lead to collisions. Since idiomatic Scala often times relies quite
heavily on implicits (for instance when working with type classes) this can present a problem.

For example, consider this snippet, which loosely resembles what we have in *spray*::

  val post: Route => Route = ...

Here ``post`` defines some logic that modifies a ``Route``. For this example it doesn't matter how ``Route`` is actually
defined. All we care about is that we can use ``post`` to wrap a ``Route`` thereby producing another ``Route``::

  val route: Route =
    post {
      ... // some inner route
    }

The ``post`` modifier is only one of many modifiers that can be freely combined. Some of them are not modelled as *vals*
but rather as *defs*, since they take some parameters. For example the ``hosts`` modifier filters requests according
to some host name::

  def host(hostName: String): Route => Route = ...

You could combine it with ``post`` like this::

  val route: Route =
    host("spray.io") {
      post {
        ... // some inner route
      }
    }

The problem arises if a modifier method requires an implicit parameter list, for example if we wanted to flexibilize
the ``host`` modifier to take any parameter that can be implicitly converted to a ``String``::

  def host[T](obj: T)(implicit ev: T => String): Route => Route = ...

At first glance this change doesn't look like it would hurt us but in fact it breaks our modifier composition! When we
now write::

  host("spray.io") {
    ... // some Route expression
  }

the compiler will interpret our inner route expression not as an argument to the ``Route => Route`` function
produced by ``host``, but rather as an explicitly specified value for the implicit parameter. Clearly this is not what
we want. We could fix this with an extra pair of parentheses like this::

  (host("spray.io")) {
    ... // some Route expression
  }

but as DSL designers this must leave us unsatisfied.

Luckily, the magnet pattern provides a nice solution. It allows us to push the implicit requirement "one level down", so
the combinability of our ``host`` modifier is fully restored::

  def host(magnet: HostMagnet) = magnet()

  sealed trait HostMagnet {
    def apply(): Route => Route
  }

  object HostMagnet {
    implicit def fromObj[T](obj: T)(implicit ev: T => String) =
      new HostMagnet {
        def apply() = ...
      }
  }

Modelled in this way the implicit parameter list on the ``host`` method is removed, which prevents it from colliding
with the ``apply`` method on the returned object (the ``Route => Route`` function in our case).

This example shows that the magnet pattern has certain applications outside of providing a mere alternative to method
overloading. Because *sprays* routing DSL relies so heavily on functions and thereby ``apply`` calls, "removing"
implicit parameter lists on DSL elements is crucial and the magnet pattern turns out to be a great asset in this regard.


Drawbacks
---------

Of course, where there is light there must also be some darkness. The magnet pattern certainly isn't an exception in
that regard. So let's look at what we have to pay in order to reap the benefits discussed above.


Verbosity
~~~~~~~~~

You probably already noticed that magnets come with a certain amount of extra verbosity. Having to introduce a dedicated
type with companion object and anonymous classes for every magnet branch is no doubt a disadvantage. Apart from the
additional lines this overhead increases code complexity, especially for other people reading your code. Someone not
familiar with the pattern might scratch his head about why you chose to jump through all these extra hoops instead of
simply resorting to "traditional" method overloading.


API "Obfuscation"
~~~~~~~~~~~~~~~~~

Somewhat related to the previous point, the magnet pattern might be perceived as actually "obfuscating" your APIs.
While with "traditional" method overloading the API of a class or trait can be easily grasped from the method signatures
the introduction of magnets pushes important parts of the API down into the "branches" on the magnet companion,
where they are scattered across several implicit conversions. Also, since parameter lists with several elements are
grouped together as tuples, where the individual members have no explicit name, important information with regard to
the semantics of the individual parameters might be lost.

Another aspect of this is that the tools you might be relying on for inspecting a method signature at the call-site
(like the "Parameter Info" view of your IDE) will not work anymore once you "magnetized" the method.


No named Parameters
~~~~~~~~~~~~~~~~~~~

Since parameters are not actually defined on the method itself you cannot address them by name, i.e. this
doesn't work (coming back to our example from the beginning)::

  complete(status = 200, obj = "All good")


Limited by-name Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~

If you have several parameters on an overload and some of them are call-by-name you cannot transform that overload into
a magnet branch and uphold the by-name property. E.g. this method cannot be directly "magnetized"::

  def bar(a: Int, b: => String)

If you have only one single call-by-name parameter things might work as expected, depending on how exactly you'd like
the parameter to be used, but there is a catch to watch out for!

Suppose we have this "traditional" definition::

  def foo(s: => String): Unit = {
    println(s)
    println(s)
  }

We *can* "magnetize" it like this::

  def foo(magnet: FooMagnet): Unit = magnet()

  sealed trait FooMagnet {
    def apply()
  }
  object FooMagnet {
    implicit def fromString(s: => String) =
      new FooMagnet {
        def apply(): Unit = {
          println(s)
          println(s)
        }
      }
  }

This compiles and, when we look at the following example, appears to be doing the same thing as its "unmagnetized"
counterpart::

  def string() = {
    print("NOT-")
    "BAD"
  }

  foo(string())

This ends up printing "NOT-BAD" twice, as expected. Now if we move the body of the ``string()`` method directly into
the argument expression of ``foo`` like this::

  foo {
    print("NOT-")
    "BAD"
  }

you might be surprised to see the output being "NOT-BAD" and "BAD" instead. The ``print("NOT-")`` line is not actually
executed during the second evaluation of the by-name parameter of the implicit ``fromString``. How come?

The reason is discussed in Scala issue `SI-3237`__. In essence: The compiler has several options of how exactly to
insert the implicit conversion and chooses the "wrong" one. Instead of generating this::

  foo {
    FooMagnet.fromString {
      print("NOT-")
      "BAD"
    }
  }

it generates this::

  foo {
    print("NOT-")
    FooMagnet.fromString {
      "BAD"
    }
  }

which is enough to make the types line up, but isn't quite what we want.
So, while "magnetizing" single by-name parameters works as expected if the argument is a single expression, the behavior
of the magnetized version differs from the unmagnetized one if the argument consists of a block with several statements.
Definitely something to be aware of!


__ https://issues.scala-lang.org/browse/SI-3237


Param List required
~~~~~~~~~~~~~~~~~~~

*(2012-12-17: Updated after feedback with corrections, see post comments below)*

The magnet pattern relies on the ability of the compiler to select one of potentially several magnet branches in order
to make an otherwise illegal call work (type-wise). In order for this logic to actually kick in we need to "provoke"
an initial type-mismatch that the compiler can overcome with an implicit conversion. This requires that we actually
have a parameter list to work with. Overloads without a parameter list, like::

  def foo: String

cannot be "magnetized". Unfortunately this also renders the magnet pattern ineffective for removing implicit parameter
lists that are not preceded by a non-implicit parameter list, something that we have to work around in several places
in :ref:`spray-routing`.

Note that this does not mean that the parameter list cannot be empty. An overload like::

  def foo(): String

can be turned into the following magnet branch without any problem::

  implicit def fromUnit(u: Unit): FooMagnet = ...


No default Parameters
~~~~~~~~~~~~~~~~~~~~~

It's not hard to picture situations where combining method overloading with default parameters leads to apparent
ambiguities that can quite significantly reduce the readability of your code. This for example::

  def foo(a: Int, b: String = "") = ...
  def foo(b: Int) = ...

is perfectly legal and compiles fine. However, the default parameter on the first overload will never actually kick in.
Moreover, someone reading your code (like yourself 6 months down the road) might easily trip over which overload is
actually being called by something like ``foo(42)``.

Additionally, even in cases without risk of ambiguities, the Scala compiler currently only allows one of all overloads
to define default parameters, otherwise you'll see a ``multiple overloaded alternatives of method foo define default
arguments`` compiler error. As explained by `this answer`__ by Lukas Rytz on the scala-user mailing list the reason for
this is a technical detail of how default parameters are currently implemented. So, potentially, this behavior could be
changed in a future Scala version. (However, I certainly wouldn't count on it.)

Unfortunately, when implementing overloading with magnets, default parameters are not available at all. Instead you'll
have to fall back to the old Java way of "unrolling" all defaults into their own overloads (i.e. magnet branches).

__ https://groups.google.com/forum/#!msg/scala-user/FyQK3-cqfaY/fXLHr8QsW_0J


No Type Inference on Arguments
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

There are situations where method overloading prevents the compiler from infering types in the way it
otherwise would. Consider this example::

  object Test {
    def foo(i: Int, f: String => String) = f(i.toString)
    def foo(d: Double, f: String => String) = f(d.toString)
  }

  Test.foo(42.0, _.trim)

This looks like a perfectly valid piece of code that the compiler should have no problem interpreting.
Let's see what happens when we paste it into the REPL::

  scala> :paste
  // Entering paste mode (ctrl-D to finish)

  object Test {
    def foo(i: Int, f: String => String) = f(i.toString)
    def foo(d: Double, f: String => String) = f(d.toString)
  }

  Test.foo(42.0, _.trim)

  // Exiting paste mode, now interpreting.

  error: missing parameter type for expanded function ((x$1) => x$1.trim)
  Test.foo(42.0, _.trim)
                 ^

The compiler cannot infer that the parameter of our anonymous function literal is a String even though there is
obviously no other option. When we remove the first overload all is well and the snippet happily compiles.
The reason for this phenomenon is buried in section "6.26.3 Overloading Resolution" of the Scala Language
Specification. You might want to check out Jasons answer to `this Stackoverflow question`__ for some
easier-to-understand explanation.

__ http://stackoverflow.com/questions/3315752/why-does-scala-type-inference-fail-here/3316091#3316091

What we can see from this example is that method overloading can blind the compiler from "seeing" the argument type
when several overloads define parameters with the same "shape" at the respective position. Unfortunately this is not
only not improved by using magnets, it is even worsened.

Let's look at an example (Scala 2.10 this time)::

  def foo(s: String): Unit = ???
  def foo(f: String => String) = println(f(" Yay!"))

  foo(_.trim)

Because the two overloads do not have the same "shape" this compiles and works as expected.
Now the same thing magnetized::

  def foo(magnet: FooMagnet) = magnet()

  sealed trait FooMagnet {
    def apply()
  }
  object FooMagnet {
    implicit def fromString(s: => String) = new FooMagnet { def apply() = ??? }
    implicit def fromFunc(f: String => String) =
      new FooMagnet {
        def apply() = println(f(" Yay!"))
      }
  }

  foo(_.trim)

This doesn't compile. We get the same ``missing parameter type for expanded function`` error as above, which shows
us that the compiler is unable to infer that our function literal is to have the type ``String => String``. When we
think again about how the magnet pattern actually works this becomes clear. The compiler is looking for an implicit
conversion from the type we specify to the magnet type. Since our ``_.trim`` argument does *not* have the type
``String => String`` (but rather some unqualified ``Function1`` type) the compiler cannot relate it to the respective
magnet branch. Therefore it has no way of fully establishing the type of our function literal and gives up.

What this shows us is that the magnet pattern only works if the type of all arguments is fully known at the call site.
Sometimes this can be inconvenient.


Conclusion
----------

Stepping back, we can conclude that the magnet pattern offers a real alternative to "traditional" method overloading.
It's an alternative that is not per se better or worse. Rather, it's simply different, with its own advantages and
disadvantages. What is nice is that most of its properties are somewhat orthogonal to traditional overloading, the two
solutions only share drawbacks in two areas (default parameters and type inference). For all other aspects one solution
can overcome the issues of the other in that area, which gives us the choice to pick whatever technique best fits the
requirements at hand. If you want you can even mix the two in one particular set of overloads. For example, you might
choose to only use magnets for overcoming an erasure-induced collision on two overloads, and leave all others as is.

So, no matter whether you see immediate application opportunities for magnets in your own code or not, we think that
the magnet pattern is a valuable technique to understand and master. If nothing else, having read about it will help
you better comprehend what's going on under the hood of *sprays* :ref:`routing DSL <spray-routing>`...

| Cheers,
| Mathias
