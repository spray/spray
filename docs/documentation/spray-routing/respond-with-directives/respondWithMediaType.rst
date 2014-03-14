.. _-respondWithMediaType-:

respondWithMediaType
====================

Overrides the media-type of the response returned by its inner route with the given one.


Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/RespondWithDirectives.scala
   :snippet: respondWithMediaType


Description
-----------

This directive transforms ``HttpResponse`` and ``ChunkedResponseStart`` messages coming back from its inner route by
overriding the ``entity.contentType.mediaType`` with the given one if the entity is non-empty.
Empty response entities are left unchanged.

If the given media-type is not accepted by the client the request is rejected with an
``UnacceptedResponseContentTypeRejection``.

.. note:: This directive removes a potentially existing ``Accept`` header from the request,
 in order to "disable" content negotiation in a potentially running ``Marshaller`` in its inner route.
 Also note that this directive does *not* change the response entity buffer content in any way,
 it merely overrides the media-type component of the entities Content-Type.


Example
-------

.. includecode:: ../code/docs/directives/RespondWithDirectivesExamplesSpec.scala
   :snippet: respondWithMediaType-examples