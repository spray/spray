.. _-rejectEmptyResponse-:

rejectEmptyResponse
===================

Replaces a response with no content with an empty rejection.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/MiscDirectives.scala
   :snippet: rejectEmptyResponse


Description
-----------

The ``rejectEmptyResponse`` directive is mostly used with marshalling ``Option[T]`` instances. The value ``None`` is
usually marshalled to an empty but successful result. In many cases ``None`` should instead be handled as
``404 Not Found`` which is the effect of using ``rejectEmptyResponse``.

Example
-------

.. includecode:: ../code/docs/directives/MiscDirectivesExamplesSpec.scala
  :snippet: rejectEmptyResponse-example
