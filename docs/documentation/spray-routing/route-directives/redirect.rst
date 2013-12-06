.. _-redirect-:

redirect
========

Completes the request with a redirection response to a given targer URI and of a given redirection type (status code).


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/RouteDirectives.scala
   :snippet: redirect


Description
-----------

``redirect`` is a convenience helper for completing the request with a redirection response.
It is equivalent to this snippet relying on the ``complete`` directive:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/RequestContext.scala
   :snippet: redirect-implementation


Example
-------

.. includecode:: ../code/docs/directives/RouteDirectivesExamplesSpec.scala
   :snippet: redirect-examples