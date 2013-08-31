.. _-decompressRequest-:

decompressRequest
=================

Will try to decompress the request with either ``Gzip``, ``Deflate``, or ``NoEncoding``
based on the presence of a matching ``Content-Encoding`` header, and assuming ``NoEncoding``
if no ``Content-Encoding`` header is present. If the request contains an incorrect
``Content-Encoding`` header it will be rejected with a ``CorruptRequestEncodingRejection``.

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
