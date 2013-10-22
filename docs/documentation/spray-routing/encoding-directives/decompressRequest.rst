.. _-decompressRequest-:

decompressRequest
=================

Decompresses the request if it is encoded with one of the given encoders.
If the request's encoding doesn't match one of the given encoders it is rejected with an
``UnsupportedRequestEncodingRejection``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/EncodingDirectives.scala
   :snippet: decompressRequest

Description
-----------

The ``decompressRequest`` directive will behave as follows:

========================================= ===============================
``Content-Encoding`` header                resulting request
========================================= ===============================
``Content-Encoding: gzip``                 decompressed
``Content-Encoding: deflate``              decompressed
``Content-Encoding: identity``             unchanged
no ``Content-Encoding`` header present     unchanged
========================================= ===============================

For an overview of the different ``decompressRequest`` directives and which one to use when,
see :ref:`WhenToUseWhichDecompressRequestDirective`.

Example
-------

.. includecode:: /../spray-routing-tests/src/test/scala/spray/routing/EncodingDirectivesSpec.scala
   :snippet: decompressRequest-example
