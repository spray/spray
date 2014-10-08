Dependencies
============

Apart from the Scala library (see :ref:`Current Versions` chapter) *spray-routing* depends on

- :ref:`spray-http`
- :ref:`spray-httpx`
- :ref:`spray-util`
- :ref:`spray-io` (optionally, required for ``SimpleRoutingApp``)
- :ref:`spray-can` (optionally, required for ``SimpleRoutingApp``)
- :ref:`spray-caching` (optionally, required for ``CachingDirectives`` and ``CachedUserPassAuthenticator``)
- shapeless_ (1.2.x)
- akka-actor 2.1.x (with 'provided' scope, i.e. you need to pull it in yourself)

.. _shapeless: https://github.com/milessabin/shapeless