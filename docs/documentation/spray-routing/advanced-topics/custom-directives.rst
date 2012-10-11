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

.. includecode:: /../spray-routing/src/main/scala/cc/spray/routing/directives/MethodDirectives.scala
   :snippet: source-quote

__ https://github.com/spray/spray/blob/master/spray-routing/src/main/scala/cc/spray/routing/directives/MethodDirectives.scala


One low-level directive that often forms the basis of some higher-level named configuration is the
:ref:`-filter-` directive.


Transforming Directives
-----------------------

