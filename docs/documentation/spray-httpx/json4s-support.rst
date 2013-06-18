json4s Support
==============

In analogy to the :ref:`spray-json-support` *spray-httpx* also provides the Json4sSupport_ and Json4sJacksonSupport_
traits, which automatically provide implicit ``Marshaller`` and ``Unmarshaller`` instances for *all* types if an
implicit ``org.json4s.Formats`` instance is in scope.

When mixing in either one of the two traits you have to implement the abstract member::

    implicit def json4sFormats: Formats

with your custom logic. See the `json4s documentation`_ for more information on how to do this.

.. note:: Since *spray-httpx* only comes with an optional dependency on `json4s-native`_ and `json4s-jackson`_ you still
   have to add either one of them to your project yourself. Check the json4s_ documentation for information on how to do
   this.


.. _Json4sSupport: https://github.com/spray/spray/blob/release/1.0/spray-httpx/src/main/scala/spray/httpx/Json4sSupport.scala
.. _Json4sJacksonSupport: https://github.com/spray/spray/blob/release/1.0/spray-httpx/src/main/scala/spray/httpx/Json4sJacksonSupport.scala
.. _json4s documentation: http://json4s.org/#serialization
.. _json4s-native: https://github.com/json4s/json4s/tree/master/native
.. _json4s-jackson: https://github.com/json4s/json4s/tree/master/jackson
.. _json4s: http://www.json4s.org