.. _-compressResponse-:

compressResponse
================

Uses the first of a given number of encodings that the client accepts.
If none are accepted the request is rejected with an ``UnacceptedResponseEncodingRejection``.
By default, if no encoders are specified, the tried list is ``Gzip``, ``Deflate`` and ``NoEncoding``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/EncodingDirectives.scala
   :snippet: compressResponse

Description
-----------

The ``compressResponse`` directive will behave as follows:

========================================= ===============================
``Accept-Encoding`` header                resulting response
========================================= ===============================
``Accept-Encoding: gzip``                 compressed with ``Gzip``
``Accept-Encoding: deflate``              compressed with ``Deflate``
``Accept-Encoding: deflate, gzip``        compressed with ``Gzip``
``Accept-Encoding: identity``             uncompressed
no ``Accept-Encoding`` header present     compressed with ``Gzip``
========================================= ===============================

For an overview of the different ``compressResponse`` directives and which one to use when,
see :ref:`WhenToUseWhichCompressResponseDirective`.

Example
-------

.. includecode:: /../spray-routing-tests/src/test/scala/spray/routing/EncodingDirectivesSpec.scala
   :snippet: compressResponse-example
