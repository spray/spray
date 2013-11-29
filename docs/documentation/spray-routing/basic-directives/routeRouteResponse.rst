.. _-routeRouteResponse-:

routeRouteResponse
==================

Replaces the message the inner route sends to the responder with the result of a new route.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/BasicDirectives.scala
   :snippet: routeRouteResponse

Description
-----------

The ``routeRouteResponse`` directive is used as a building block for :ref:`Custom Directives` to replace what
the inner route sends to the responder (see :ref:`The Responder Chain`) with the result of a completely new route.

See :ref:`Responder Chain Directives` for similar directives.

Example
-------

.. includecode:: ../code/docs/directives/BasicDirectivesExamplesSpec.scala
   :snippet: routeRouteResponse
