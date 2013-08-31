.. _-compressResponseWith-:

compressResponseWith
====================

Always compresses the response with the first of the specified Encoders that matches
the ``Accept-Encoding`` request header or with the first Encoder if no
``Accept-Encoding`` header is present. Will reject requests if it contains a
``Accept-Encoding`` header that does not match any of the specified Encoders.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/EncodingDirectives.scala
   :snippet: compressResponseWith

Description
-----------

The ``compressResponseWith(Gzip, Deflate)`` directive will behave as follows:

========================================= ========================================================
``Accept-Encoding`` header                resulting response
========================================= ========================================================
``Accept-Encoding: gzip``                 compressed with ``Gzip``
``Accept-Encoding: deflate``              compressed with ``Deflate``
``Accept-Encoding: deflate, gzip``        compressed with ``Gzip``
``Accept-Encoding: identity``             rejected with ``UnacceptedResponseEncodingRejection``
no ``Accept-Encoding`` header present     compressed with ``Gzip``
========================================= ========================================================

For an overview of the different ``compressResponse`` directives and which one to use when,
see :ref:`WhenToUseWhichCompressResponseDirective`.

Example
-------

.. includecode:: /../spray-routing-tests/src/test/scala/spray/routing/EncodingDirectivesSpec.scala
   :snippet: compressResponseWith-example
