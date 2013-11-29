.. _-decompressRequest-:

decompressRequest
=================

Decompresses the request if it is can be decoded with one of the given decoders. Otherwise,
the request is rejected with an ``UnsupportedRequestEncodingRejection(supportedEncoding)``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/EncodingDirectives.scala
   :snippet: decompressRequest

Description
-----------

The ``decompressRequest`` directive allows either to specify a list of decoders or none at all. If
no ``Decoder`` is specified ``Gzip``, ``Deflate``, or ``NoEncoding`` will be tried.

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

This example shows the behavior of ``decompressRequest()`` without any decoders specified:

.. includecode:: ../code/docs/directives/EncodingDirectivesExamplesSpec.scala
   :snippet: decompressRequest-0

This example shows the behaviour of ``decompressRequest(Gzip, NoEncoding)``:

.. includecode:: ../code/docs/directives/EncodingDirectivesExamplesSpec.scala
   :snippet: decompressRequest-1
