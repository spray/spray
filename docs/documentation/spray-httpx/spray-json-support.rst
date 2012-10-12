.. _spray-json-support:

spray-json Support
==================

The SprayJsonSupport_ trait provides a ``Marshaller`` and ``Unmarshaller`` for every type ``T`` that an implicit
``spray.json.RootJsonReader`` and/or ``spray.json.RootJsonWriter`` (respectively) is available for.

Just mix in ``spray.httpx.SprayJsonSupport`` or ``import spray.httpx.SprayJsonSupport._``.

For example:

.. includecode:: code/docs/SprayJsonSupportExamplesSpec.scala
   :snippet: example-1

If you bring an implicit ``spray.json.JsonPrinter`` into scope the marshaller will use it. Otherwise it uses the
default ``spray.json.PrettyPrinter``.

.. note:: Since *spray-httpx* only comes with an optional dependency on spray-json_ you still have to add it to your
   project yourself. Check the spray-json_ documentation for information on how to do this.


.. _SprayJsonSupport: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/SprayJsonSupport.scala
.. _spray-json: https://github.com/spray/spray-json