.. _-mapRouteResponse-:

mapRouteResponse
================

Changes the message the inner route sends to the responder.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/BasicDirectives.scala
   :snippet: mapRouteResponse

Description
-----------

The ``mapRouteResponse`` directive is used as a building block for :ref:`Custom Directives` to transform what
the inner route sends to the responder (see :ref:`The Responder Chain`).

See :ref:`Responder Chain Directives` for similar directives.

Example
-------

.. includecode:: ../code/docs/directives/BasicDirectivesExamplesSpec.scala
   :snippet: 0mapRouteResponse
