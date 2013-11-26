.. _-mapRouteResponsePF-:

mapRouteResponsePF
==================

Changes the message the inner route sends to the responder.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/BasicDirectives.scala
   :snippet: mapRouteResponsePF

Description
-----------

The ``mapRouteResponsePF`` directive is used as a building block for :ref:`Custom Directives` to transform what
the inner route sends to the responder (see :ref:`The Responder Chain`). It's similar to the :ref:`-mapRouteResponse-`
directive but allows to specify a partial function that doesn't have to handle all the incoming response messages.

See :ref:`Responder Chain Directives` for similar directives.

Example
-------

.. includecode:: ../code/docs/directives/BasicDirectivesExamplesSpec.scala
   :snippet: mapRouteResponsePF
