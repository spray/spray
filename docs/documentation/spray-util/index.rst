.. _spray-util:

spray-util
==========

The *spray-util* module contains a number of smaller helper classes that are used by all other *spray* modules,
except :ref:`spray-http`, which is kept intentionally free of other *spray* dependencies.


Dependencies
------------

Apart from the Scala library (see :ref:`current-versions` chapter) *spray-util* only depends on
*akka-actor* (with 'provided' scope, i.e. you need to pull it in yourself).



Installation
------------

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-util* into your classpath.

Afterwards just ``import spray.util._`` to bring all relevant identifiers into scope.


Pimps
-----

*spray-util* provides a number of convenient "extensions" to standard Scala and Akka classes.

The currently available pimps can be found here__. Their hooks are placed in the ``spray.util`` `package object`__,
you bring them in scope with the following import::

  import spray.util._

__ https://github.com/spray/spray/tree/master/spray-util/src/main/scala/spray/util/pimps
__ https://github.com/spray/spray/blob/master/spray-util/src/main/scala/spray/util/package.scala

.. admonition:: Side Note

   Even though now officially somewhat frowned upon due to its arguably limited PC-ness we still like the term "pimps"
   for these, since it honors the origins of the technique (the "pimp-my-library" pattern, as it was originally coined
   by Martin Odersky in a `short article`__ in late 2006) and provides a very succinct and, in the scala community,
   well-known label for it.

__ http://www.artima.com/weblogs/viewpost.jsp?thread=179766


Akka Helpers
------------

*spray-util* comes with a few utility tools for working with Akka:

- UnregisteredActorRef_
- `Reply.withContext`_
- LoggingContext_

.. _UnregisteredActorRef:

UnregisteredActorRef
~~~~~~~~~~~~~~~~~~~~

The UnregisteredActorRef__ is an ActorRef, which

- offers the ability to hook caller-side logic into the reply message path
- is never registered anywhere, i.e. can be GCed as soon the receiver drops it or is GCed itself

When you send a message to an Actor with ``receiver.tell(msg, sender)`` both the receiver as well as the sender have
to be ActorRefs. Sometimes, however, you might want to inject logic, which is local to the call site of the ``tell``,
into the message stream coming back from the receiver as replies to the message told.

Check out this example:

.. includecode:: code/docs/UtilExamplesSpec.scala
   :snippet: example-1

In this example the reply of an actor is channeled to a second actor, which simply logs it to the console.
Now suppose that you want to modify the replies from the first actor before they reach the second actor.
You could do it this way:

.. includecode:: code/docs/UtilExamplesSpec.scala
   :snippet: example-2

This works but has a number of disadvantages. Firstly, we have to spin up a full actor just to inject the modification
logic into the reply message path. Even though Actors are lightweight the overhead of creation and teardown can be
significant in high-throughput, more low-level layers of an application. Also, since our transformation logic is a
simple function without any internal state we are not really making use of the Actors ability to provide a
"safe container" for mutable state in an otherwise parallel execution environment.

More importantly though the actor in this scenario has the disadvantage of being registered with its system. This is
great for being able to look it up by its path or address it remotely, but it requires that we shut it down when its
not needed anymore. It cannot automatically be garbage collected. This means that we have to supply it with some logic
determining when to shut down. In our example we simply shut down after the first message.
What if we wanted to apply our modification logic to *all* messages that our first actor sends as replies to the second,
even if their number is not known a priori? If we have a way of determining which one is the last one, we can use it
for the shutdown, but what if we don't?

With *sprays* UnregisteredActorRef we could inject the transformation logic like this:

.. includecode:: code/docs/UtilExamplesSpec.scala
   :snippet: example-3

Essentially the UnregisteredActorRef allows us to wrap custom logic into an ActorRef, which you can inject into
message paths and which is very lightweight, because it is *not* registered. In our example this allows our modification
function to stay in place for as long as the first actor holds on to its reference. As soon as its reference is dropped
it can be GCed. No need to supply shutdown logic.

.. caution:: Since an ``UnregisteredActorRef`` is not registered it is *not* addressable from a non-local JVM
   (i.e. remotely) and it also breaks some otherwise valid Akka invariants like
   ``system.actorFor(ref.path.toString).equals(ref)`` in the local-only context.
   It should therefore be used only in purely local environments and in full consideration of its limitations.

   However, it is possible to make an ``UnregisteredActorRef`` reachable remotely by explicitly wrapping it with a
   registered ``ActorRef``. The ``UnregisteredActorRef`` provides three different ``register...`` methods for this
   purpose (check `the sources`_ for more details on this).

__ `the sources`_
.. _the sources: https://github.com/spray/spray/blob/master/spray-util/src/main/scala/akka/spray/UnregisteredActorRef.scala


Reply.withContext
~~~~~~~~~~~~~~~~~

The ``Reply.withContext`` helper builds upon UnregisteredActorRef_ to attach "context" objects to all replies coming
back from an Actor as response to a specific tell.

For example:

.. includecode:: code/docs/UtilExamplesSpec.scala
   :snippet: example-4

So, by using a ``Reply.withContext`` call as the sender of a ``tell`` you can attach a custom "context" object to a
message, which you are going to receive together with each reply messages in an instance of the ``Reply`` case class.
This can be very handy in a number of situations, where you'd like to channel some local context through a
request/response cycle with another actor. The overhead introduced by this mechanism of context keeping is really
small, which makes it a viable solution for *local-only* messaging protocols.

.. caution:: Since ``Reply.withContext`` uses an UnregisteredActorRef underneath all the restrictions of such
   special ActorRefs (as discussed in the previous section) apply.
   It should therefore be used only in purely local environments and in full consideration of its limitations.


LoggingContext
~~~~~~~~~~~~~~

The LoggingContext is a simple ``akka.event.LoggingAdapter`` that can be implicitly created from ActorRefFactory
instances (i.e. ActorSystems or ActorContexts). It is mainly used by :ref:`spray-routing` directives, which require
a logging facility for either type of ActorRefFactory.