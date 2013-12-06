.. _-requestInstance-:

requestInstance
===============

Extracts the complete ``HttpRequest`` instance.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: requestInstance


Description
-----------

Use ``requestUri`` to extract just the complete URI of the request. Usually there's little use of
extracting the complete request because extracting of most of the aspects of HttpRequests is handled by specialized
directives. See :ref:`Request Directives`.


Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: requestInstance-example
