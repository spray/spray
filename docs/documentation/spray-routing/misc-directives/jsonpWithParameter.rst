.. _-jsonpWithParameter-:

jsonpWithParameter
==================

Wraps a response of type ``application/json`` with an invocation to a callback function which name is given as an
argument. The new type of the response is ``application/javascript``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: jsonpWithParameter


Description
-----------

Find more information about JSONP in Wikipedia_. Note that JSONP is not considered the solution of choice for
many reasons. Be sure to understand its drawbacks and security implications.

.. _Wikipedia: http://en.wikipedia.org/wiki/JSONP


Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: jsonpWithParameter-example
