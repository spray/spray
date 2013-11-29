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
such as::

    val delete = method(DELETE)
    val get = method(GET)
    val head = method(HEAD)
    val options = method(OPTIONS)
    val patch = method(PATCH)
    val post = method(POST)
    val put = method(PUT)

__ https://github.com/spray/spray/blob/release/1.0/spray-routing/src/main/scala/spray/routing/directives/MethodDirectives.scala


The low-level directives that most often form the basis of higher-level "named configuration" directives are grouped
together in the :ref:`BasicDirectives` trait.


Transforming Directives
-----------------------

The second option for creating new directives is to transform an existing one using one of the "transformation methods",
which are defined on the Directive__ class, the base class of all "regular" directives.

__ https://github.com/spray/spray/blob/release/1.0/spray-routing/src/main/scala/spray/routing/Directive.scala

Apart from the combinator operators (``|`` and ``&``) and the case-class extractor (``as[T]``) there are these
transformations defined on all ``Directive[L <: HList]`` instances:

- `map / hmap`_
- `flatMap / hflatMap`_
- `require / hrequire`_
- `recover / recoverPF`_

map / hmap
~~~~~~~~~~

The ``hmap`` modifier has this signature (somewhat simplified)::

    def hmap[R](f: L => R): Directive[R :: HNil]

It can be used to transform the ``HList`` of extractions into another ``HList``. The number and/or types of the
extractions can be changed arbitrarily. If ``R <: HList`` then the result is ``Directive[R]``.
Here is a somewhat contrived example:

.. includecode:: ../code/docs/CustomDirectiveExamplesSpec.scala
   :snippet: example-1

If the Directive is a single-value Directive, i.e. one that extracts exactly one value, you can also use the simple
``map`` modifier, which doesn't take the directives ``HList`` as parameter but rather the single value itself.

One example of a predefined directive relying on ``map`` is the :ref:`-optionalHeaderValue-` directive.


flatMap / hflatMap
~~~~~~~~~~~~~~~~~~

With ``hmap`` or ``map`` you can transform the values a directive extracts, but you cannot change the "extracting"
nature of the directive. For example, if you have a directive extracting an ``Int`` you can use ``map`` to turn it into
a directive that extracts that ``Int`` and doubles it, but you cannot transform it into a directive, that doubles all
positive ``Int`` values and rejects all others.

In order to do the latter you need ``hflatMap`` or ``flatMap``. The ``hflatMap`` modifier has this signature::

    def hflatMap[R <: HList](f: L => Directive[R]): Directive[R]

The given function produces a new directive depending on the ``HList`` of extractions of the underlying one.
As in the case of `map / hmap`_ there is also a single-value variant called ``flatMap``, which simplifies the operation
for Directives only extracting one single value.

Here is the (contrived) example from above, which doubles positive ``Int`` values and rejects all others:

.. includecode:: ../code/docs/CustomDirectiveExamplesSpec.scala
   :snippet: example-2

A common pattern that relies on ``flatMap`` is to first extract a value from the ``RequestContext`` with the
:ref:`-extract-` directive and then ``flatMap`` with some kind of filtering logic. For example, this is the
implementation of the :ref:`-method-` directive:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MethodDirectives.scala
   :snippet: method-directive

The explicit type parameter ``[HNil]`` on the ``flatMap`` is needed in this case because the result of the ``flatMap``
is directly concatenated with the :ref:`-cancelAllRejections-` directive, thereby preventing "outside-in" inference of
the type parameter value.


require / hrequire
~~~~~~~~~~~~~~~~~~

The ``require`` modifier transforms a single-extraction directive into a directive without extractions, which filters
the requests according the a predicate function. All requests, for which the predicate is ``false`` are rejected, all
others pass unchanged.

The signature of ``require`` is this (slightly simplified)::

    def require[T](predicate: T => Boolean): Directive[HNil]

One example of a predefined directive relying on ``require`` is the first overload of the :ref:`-host-` directive.

You can only call ``require`` on single-extraction directives. The ``hrequire`` modifier is the more general variant,
which takes a predicate of type ``HList => Boolean``.
It can therefore also be used on directives with several extractions.


recover / recoverPF
~~~~~~~~~~~~~~~~~~~

The ``recover`` modifier allows you "catch" rejections produced by the underlying directive and, instead of rejecting,
produce an alternative directive with the same type(s) of extractions.

The signature of ``recover`` is this::

    def recover(recovery: List[Rejection] => Directive[L]): Directive[L]

In many cases the very similar ``recoverPF`` modifier might be little bit easier to use since it doesn't require the
handling of *all* rejections::

    def recoverPF(recovery: PartialFunction[List[Rejection], Directive[L]]): Directive[L]

One example of a predefined directive relying ``recoverPF`` is the :ref:`-optionalHeaderValue-` directive.


Directives from Scratch
-----------------------

The third option for creating custom directives is to do it "from scratch", by directly subclassing the ``Directive``
class. The ``Directive`` is defined like this (leaving away operators and modifiers)::

    abstract class Directive[L <: HList] {
      def happly(f: L => Route): Route
    }

It only has one abstract member that you need to implement, the ``happly`` method, which creates the ``Route`` the
directives presents to the outside from its inner Route building function (taking the extractions as parameter).

Extractions are kept as a shapeless_ ``HList``. Here are a few examples:

.. rst-class:: wide

- A ``Directive[HNil]`` extracts nothing (like the ``get`` directive). Because this type is used quite frequently
  *spray-routing* defines a type alias for it::

    type Directive0 = Directive[HNil]

- A ``Directive[String :: HNil]`` extracts one ``String`` value (like the :ref:`-hostName-` directive).
  The type alias for it is::

    type Directive1[T] = Directive[T :: HNil]

- A ``Directive[Int :: String :: HNil]`` extracts an ``Int`` value and a ``String`` value
  (like a ``parameters('a.as[Int], 'b.as[String]`` directive).

Keeping extractions as *HLists* has a lot of advantages, mainly great flexibility while upholding full type safety and
"inferability". However, the number of times where you'll really have to fall back to defining a directive from scratch
should be very small. In fact, if you find yourself in a position where a "from scratch" directive is your only option,
we'd like to hear about it, so we can provide a higher-level "something" for other users.


.. _shapeless: https://github.com/milessabin/shapeless