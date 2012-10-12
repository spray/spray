.. _Custom Directives:

Custom Directives
=================

Part of *spray-routings* power comes from the ease with which it's possible to define custom directives at differing
levels of abstraction. There are essentially three ways of creating custom directives:

1. By introducing new "labels" for configurations of existing directives
2. By transforming existing directives
3. By writing a directive "from scratch"


Configuration Labelling
-----------------------

The easiest way to create a custom directive is to simply assign a new name for a certain configuration of one or more
existing directives. In fact, most of *spray-routings* predefined directives can be considered named configurations
of more low-level directives.

The basic technique is explained in the chapter about :ref:`Composing Directives`, where, for example, a new directive
``getOrPut`` is defined like this::

    val getOrPut = get | put

Another example are the MethodDirectives__, which are simply instances of a preconfigured :ref:`-method-` directive,
such as:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MethodDirectives.scala
   :snippet: source-quote

__ https://github.com/spray/spray/blob/master/spray-routing/src/main/scala/spray/routing/directives/MethodDirectives.scala


The low-level directives that most often form the basis of higher-level "named configuration" directives are grouped
together in the :ref:`BasicDirectives` trait, with the :ref:`-filter-` directive probably being the most prominent one.


Transforming Directives
-----------------------

The second option for creating new directives is to transform an existing one using one of the "transformation methods",
which are defined on the Directive__ class, the base class of all "regular" directives.

__ https://github.com/spray/spray/blob/master/spray-routing/src/main/scala/spray/routing/Directive.scala

Apart from the combinator operators (``|`` and ``&``) and the case-class extractor (``as[T]``) there are these
transformations defined on all ``Directive[L <: HList]`` instances:

- map_
- flatMap_
- unwrapFuture_
- `require / hrequire`_

map
~~~

The ``map`` modifier has this signature (somewhat simplified)::

    def map[R](f: L => R): Directive[R :: HNil]

It can be used to transform the ``HList`` of extractions into another ``HList``. The number and/or types of the
extractions can be changed arbitrarily. If ``R <: HList`` then the result is ``Directive[R]``.
Here is a somewhat contrived example:

.. includecode:: ../code/docs/CustomDirectiveExamplesSpec.scala
   :snippet: example-1

One example of a predefined directive relying ``map`` is the :ref:`-optionalCookie-` directive.


flatMap
~~~~~~~

With ``map`` you can transform the values a directive extracts, but you cannot change the "extracting" nature of
the directive. For example, if you have a directive extracting an ``Int`` you can use ``map`` to turn it into a
directive that extracts that ``Int`` and doubles it, but you cannot transform it into a directive, that doubles all
positive ``Int`` values and rejects all others.

In order to do the latter you need ``flatMap``. The ``flatMap`` modifier has this signature::

    def flatMap[R <: HList](f: L => Directive[R]): Directive[R]

The given function produces a new directive depending on the ``HList`` of extractions of the underlying one.
Here is the (contrived) example from the paragraph above:

.. includecode:: ../code/docs/CustomDirectiveExamplesSpec.scala
   :snippet: example-2

One example of a predefined directive relying ``flatMap`` is the :ref:`-authenticate-` directive.


unwrapFuture
~~~~~~~~~~~~

Sometimes a directive depends on results from other services, which might not be readily available.
For example, in order to :ref:`-authenticate-` a user the application might have to talk to a database or an LDAP
server. Since usually this cannot be done synchronously the "other service" might return a ``Future`` of its result,
that the directive then needs to "hook into".

The ``unwrapFuture`` modifier performs exactly this "hooking into a future" by transforming a
``Directive[Future[T] :: HNil]`` into the corresponding ``Directive[T :: HNil]``. If ``T <: HList`` then
the result is a ``Directive[T]``. This allows you to unwrap a Future of several extractions.

One example of a predefined directive relying ``unwrapFuture`` is the :ref:`-authenticate-` directive.


require / hrequire
~~~~~~~~~~~~~~~~~~

The ``require`` modifier transforms a single-extraction directive into a directive without extractions, which filters
the requests according the a predicate function. All requests, for which the predicate is ``false`` are rejected, all
others pass unchanged.

The signature of ``require`` is this (slightly simplified)::

    def require[T](predicate: T => Boolean): Directive[HNil]

You can only call ``require`` on single-extraction directives.

The ``hrequire`` modifier is the more general variant, which takes a predicate of type ``HList => Boolean``.
It can therefore also be used on directives with several extractions.


Directives from Scratch
-----------------------

The third option for creating custom directives is to do it "from scratch", by directly subclassing the ``Directive``
class. The ``Directive`` is defined like this (leaving away operators and modifiers)::

    abstract class Directive[L <: HList] {
      def happly(f: L => Route): Route
    }

It only has one abstract member that you need to implement, the ``happly`` method, which creates the ``Route``, the
directives presents to the outside, from its inner Route building function (taking the extractions as parameter).

Extractions are kept as a shapeless_ ``HList``. Here are a few examples:

.. rst-class:: wide

- A ``Directive[HNil]`` extracts nothing (like the ``get`` directive). Because this type is used quite frequently
  *spray-routing* defines a type alias for it::

    type Directive0 = Directive[HNil]

- A ``Directive[String :: HNil]`` extracts one ``String`` value (like the :ref:`-hostName-` directive).

- A ``Directive[Int :: String :: HNil]`` extracts an ``Int`` value and a ``String`` value
  (like a ``parameters('a.as[Int], 'b.as[String]`` directive).

Keeping extractions as *HLists* has a lot of advantages, mainly great flexibility while upholding full type safety and
"inferability". However, the number of times where you'll really have to fall back to defining a directive from scratch
should be very small. In fact, if you find yourself in a position where a "from scratch" directive is your only option,
we'd like to hear about it, so we can provide a higher-level "something" for other users.


.. _shapeless: https://github.com/milessabin/shapeless