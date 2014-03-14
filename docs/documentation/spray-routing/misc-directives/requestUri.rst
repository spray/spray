.. _-requestUri-:

requestUri
==========

Access the full URI of the request.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: requestUri


Description
-----------

Use :ref:`SchemeDirectives`, :ref:`HostDirectives`, :ref:`PathDirectives`,  and :ref:`ParameterDirectives` for more
targeted access to parts of the URI.


Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: requestUri-example
