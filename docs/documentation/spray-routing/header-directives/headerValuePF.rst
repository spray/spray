.. _-headerValuePF-:

headerValuePF
=============

Calls the specified partial function with the first request header the function is ``isDefinedAt`` and extracts the
result of calling the function.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/HeaderDirectives.scala
   :snippet: headerValuePF

Description
-----------

The ``headerValuePF`` directive is an alternative syntax version of ``headerValue``.  If the function throws an
exception the request is rejected with a ``MalformedHeaderRejection``. If the function is not defined for
any header the request is rejected as "NotFound".

Example
-------

.. includecode:: ../code/docs/directives/HeaderDirectivesExamplesSpec.scala
   :snippet: headerValuePF-0
