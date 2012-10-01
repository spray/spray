Dependencies
============

Apart from the Scala library (see :ref:`current-versions` chapter) *spray-httpx* depends on

- :ref:`spray-http`
- :ref:`spray-util`
- `MIME pull`_
- akka-actor (with 'provided' scope, i.e. you need to pull it in yourself)
- Optionally (you need to provide these if you'd like to use the respective *spray-httpx* feature):

  * spray-json_ (for SprayJsonSupport)
  * lift-json_ (for LiftJsonSupport)
  * twirl-api_ (for TwirlSupport)

.. _MIME pull: http://mimepull.java.net/
.. _spray-json: https://github.com/spray/spray-json
.. _lift-json: https://github.com/lift/lift/tree/master/framework/lift-base/lift-json/
.. _twirl-api: https://github.com/spray/twirl
