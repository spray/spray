lift-json Support
=================

In analogy to the :ref:`spray-json-support` *spray-httpx* also provides the LiftJsonSupport_ trait, which
automatically provides ``Marshaller`` and ``Unmarshaller`` objects if an implicit ``net.liftweb.json.Formats``
instance is in scope.

When mixing in ``LiftJsonSupport`` you have to implement the abstract member::

    def liftJsonFormats: Formats

with your custom logic.

.. note:: Since *spray-httpx* only comes with an optional dependency on lift-json_ you still have to add it to your
   project yourself. Check the lift-json_ documentation for information on how to do this.


.. _LiftJsonSupport: https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/LiftJsonSupport.scala
.. _lift-json: https://github.com/lift/lift/tree/master/framework/lift-base/lift-json/