Dependencies
============

Apart from the Scala library (see :ref:`Current Versions` chapter) *spray-httpx* depends on

- :ref:`spray-http`
- :ref:`spray-util`
- :ref:`spray-io` (only required until the upgrade to Akka 2.2, will go away afterwards)
- `MIME pull`_
- akka-actor 2.1.x (with 'provided' scope, i.e. you need to pull it in yourself)
- Optionally (you need to provide these if you'd like to use the respective *spray-httpx* feature):

  * spray-json_ (for SprayJsonSupport)
  * lift-json_ (for LiftJsonSupport)
  * twirl-api_ (for TwirlSupport)
  * json4s-native_ (for Json4sSupport)
  * json4s-jackson_ (for Json4sJacksonSupport)

.. _MIME pull: http://mimepull.java.net/
.. _spray-json: https://github.com/spray/spray-json
.. _lift-json: https://github.com/lift/lift/tree/master/framework/lift-base/lift-json/
.. _twirl-api: https://github.com/spray/twirl
.. _json4s-native: json4s-jackson_
.. _json4s-jackson: https://github.com/json4s/json4s
