.. _json4s-support:

json4s Support
==================

The Json4sSupport_ and Json4sJacksonSupport traits provide a ``Marshaller`` and ``Unmarshaller`` for every type ``T`` for every type that can be serialized using json4s_ formats


For example:

.. includecode:: code/docs/Json4sSupportExamplesSpec.scala
   :snippet: example-1

For including your own marshallers for specific types consult json4s documentation - http://json4s.org/#serialization


.. note:: Since *spray-httpx* only comes with an optional dependency on json4s-native_ and json4s-jackson_ you still have to add it to your
   project yourself. Check the spray-json_ documentation for information on how to do this.


.. _Json4sSupport: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/Json4sSupport.scala
.. _Json4sJacksonSupport: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/Json4sJacksonSupport.scala

.. _json4s: http://www.json4s.org
..  json4s-native https://github.com/json4s/json4s/tree/master/native
..  json4s-jackson https://github.com/json4s/json4s/tree/master/jackson