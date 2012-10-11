Dependencies
============

Apart from the Scala library (see :ref:`current-versions` chapter) *spray-routing* depends on

- :ref:`spray-http`
- :ref:`spray-httpx`
- :ref:`spray-util`
- :ref:`spray-caching` (optionally, required for ``CachingDirectives``)
- shapeless_
- Scalate_ (optionally, required for ``ScalateSupport``)
- akka-actor (with 'provided' scope, i.e. you need to pull it in yourself)


.. _shapeless: https://github.com/milessabin/shapeless
.. _Scalate: http://scalate.fusesource.org/