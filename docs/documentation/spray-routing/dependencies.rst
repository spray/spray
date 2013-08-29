Dependencies
============

Apart from the Scala library (see :ref:`Current Versions` chapter) *spray-routing* depends on

- :ref:`spray-http`
- :ref:`spray-httpx`
- :ref:`spray-util`
- :ref:`spray-io` (only required until the upgrade to Akka 2.2, will go away afterwards)
- :ref:`spray-can` (optionally, required for ``SimpleRoutingApp``)
- :ref:`spray-caching` (optionally, required for ``CachingDirectives``)
- shapeless_
- akka-actor 2.2.0 **RC1** (with 'provided' scope, i.e. you need to pull it in yourself). Note, that
  Akka 2.2.0 final is **not supported** because of binary incompatibilities between RC1 and the final version.
  Please use a recent one of the :ref:`nightly-builds` with Akka 2.2.0 final.

.. _shapeless: https://github.com/milessabin/shapeless
