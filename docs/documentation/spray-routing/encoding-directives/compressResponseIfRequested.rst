.. _-compressResponseIfRequested-:

compressResponseIfRequested
===========================

Only compresses the response when specifically requested by the
``Accept-Encoding`` request header (i.e. the default is "no compression").

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/EncodingDirectives.scala
   :snippet: compressResponseIfRequested

Description
-----------

The ``compressResponseIfRequested`` directive will behave as follows:

========================================= ===============================
``Accept-Encoding`` header                resulting response
========================================= ===============================
``Accept-Encoding: gzip``                 compressed with ``Gzip``
``Accept-Encoding: deflate``              compressed with ``Deflate``
``Accept-Encoding: deflate, gzip``        compressed with ``Gzip``
``Accept-Encoding: identity``             uncompressed
no ``Accept-Encoding`` header present     uncompressed
========================================= ===============================

For an overview of the different ``compressResponse`` directives and which one to use when,
see :ref:`WhenToUseWhichCompressResponseDirective`.

Example
-------

.. includecode:: /../spray-routing-tests/src/test/scala/spray/routing/EncodingDirectivesSpec.scala
   :snippet: compressResponseIfRequested-example
