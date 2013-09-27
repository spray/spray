.. _MarshallingDirectives:

Marshalling Directives
======================

Marshalling directives work in conjunction with ``spray.httpx.marshalling`` and ``spray.httpx.unmarshalling`` to convert a request entity to a specific type or a type to a response.  See :ref:`marshalling <marshalling>` and :ref:`unmarshalling <unmarshalling>` for specific serialization (also known as pickling) guidance.  

Marshalling directives usually rely on an in-scope implicit marshaller to handle conversion.  

.. toctree::
   :maxdepth: 1

   entity
   produce
   handleWith

Understanding Specific Marshalling Directives
---------------------------------------------

======================================= =======================================
directive                               behavior
======================================= =======================================
:ref:`-entity-`                         Unmarshalls the request entity to the given type and passes it to its inner route.  Used in conjection with *as* to convert requests to objects.  
:ref:`-produce-`                        Uses a marshaller for a given type to produce a completion function for an inner route. Used in conjuction with *instanceOf* to format responses.
:ref:`-handleWith-`                     Completes a request with a given function, using an in-scope unmarshaller for an input and in-scope marshaller for the output.
======================================= =======================================




