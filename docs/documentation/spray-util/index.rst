.. _spray-util:

spray-util
==========

The *spray-util* module contains a number of smaller helper classes that are used by all other *spray* modules,
except :ref:`spray-http`, which is kept intentionally free of other *spray* dependencies.


Dependencies
------------

Apart from the Scala library (see :ref:`Current Versions` chapter) *spray-util* only depends on
*akka-actor* (with 'provided' scope, i.e. you need to pull it in yourself).



Installation
------------

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-util* into your classpath.

Afterwards just ``import spray.util._`` to bring all relevant identifiers into scope.


Configuration
-------------

Just like Akka *spray-util* relies on the `typesafe config`_ library for configuration. As such its JAR contains a
``reference.conf`` file holding the default values of all configuration settings. In your application you typically
provide an ``application.conf``, in which you override Akka and/or *spray* settings according to your needs.

.. note:: Since *spray* uses the same configuration technique as Akka you might want to check out the
   `Akka Documentation on Configuration`_.

.. _typesafe config: https://github.com/typesafehub/config
.. _Akka Documentation on Configuration: http://doc.akka.io/docs/akka/2.0.4/general/configuration.html

This is the ``reference.conf`` of the *spray-util* module:

.. literalinclude:: /../spray-util/src/main/resources/reference.conf
   :language: bash



Pimps
-----

*spray-util* provides a number of convenient "extensions" to standard Scala and Akka classes.

The currently available pimps can be found here__. Their hooks are placed in the ``spray.util`` `package object`__,
you bring them in scope with the following import::

  import spray.util._

__ https://github.com/spray/spray/tree/release/1.1/spray-util/src/main/scala/spray/util/pimps
__ https://github.com/spray/spray/blob/master/spray-util/src/main/scala/spray/util/package.scala

.. admonition:: Side Note

   Even though now officially somewhat frowned upon due to its arguably limited PC-ness we still like the term "pimps"
   for these, since it honors the origins of the technique (the "pimp-my-library" pattern, as it was originally coined
   by Martin Odersky in a `short article`__ in late 2006) and provides a very succinct and, in the scala community,
   well-known label for it.

__ http://www.artima.com/weblogs/viewpost.jsp?thread=179766


LoggingContext
--------------

The ``LoggingContext`` is a simple ``akka.event.LoggingAdapter`` that can be implicitly created from ``ActorRefFactory``
instances (i.e. ``ActorSystems`` or ``ActorContexts``). It is mainly used by :ref:`spray-routing` directives, which
require a logging facility for either type of ``ActorRefFactory``.

The ``LoggingContext`` allows for some deeper configuration via the ``log-actor-paths-with-dots`` and
``log-actor-system-name`` config settings shown in the "Configuration" section above.


SprayActorLogging
-----------------

The ``SprayActorLogging`` trait is a drop-in replacement for the ``akka.actor.ActorLogging`` trait, which provides
logging via a ``LoggingContext`` and therefore supports the same configuration options.