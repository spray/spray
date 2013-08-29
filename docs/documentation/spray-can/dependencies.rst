Dependencies
============

Apart from the Scala library (see :ref:`Current Versions` chapter) *spray-can* depends on

- :ref:`spray-io`
- :ref:`spray-http`
- :ref:`spray-util`
- akka-actor 2.2.0 **RC1** (with 'provided' scope, i.e. you need to pull it in yourself). Note, that
  Akka 2.2.0 final is **not supported** because of binary incompatibilities between RC1 and the final version.
  Please use a recent one of the :ref:`nightly-builds` with Akka 2.2.0 final.
