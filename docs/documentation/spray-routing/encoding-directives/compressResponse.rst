.. _-compressResponse-:

compressResponse
================

Uses the first of a given number of encodings that the client accepts. If none are accepted the request
is rejected with an ``UnacceptedResponseEncodingRejection``.

Signature
---------

::

    def compressResponse()(implicit refFactory: ActorRefFactory): Directive0
    def compressResponse(e1: Encoder)(implicit refFactory: ActorRefFactory): Directive0
    def compressResponse(e1: Encoder, e2: Encoder)(implicit refFactory: ActorRefFactory): Directive0
    def compressResponse(e1: Encoder, e2: Encoder, e3: Encoder)(implicit refFactory: ActorRefFactory): Directive0

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

The ``compressResponse`` directive allows to specify zero to three encoders to try in the specified order.
If none are specified the tried list is ``Gzip``, ``Deflate``, and then ``NoEncoding``.

The ``compressResponse()`` directive (without an explicit list of encoders given) will therefore behave as follows:

========================================= ===============================
``Accept-Encoding`` header                resulting response
========================================= ===============================
``Accept-Encoding: gzip``                 compressed with ``Gzip``
``Accept-Encoding: deflate``              compressed with ``Deflate``
``Accept-Encoding: deflate, gzip``        compressed with ``Gzip``
``Accept-Encoding: identity``             uncompressed
no ``Accept-Encoding`` header present     compressed with ``Gzip``
========================================= ===============================

For an overview of the different ``compressResponse`` directives see :ref:`WhenToUseWhichCompressResponseDirective`.

Example
-------

This example shows the behavior of ``compressResponse`` without any encoders specified:

.. includecode:: ../code/docs/directives/EncodingDirectivesExamplesSpec.scala
   :snippet: compressResponse-1

This example shows the behaviour of ``compressResponse(Gzip)``:

.. includecode:: ../code/docs/directives/EncodingDirectivesExamplesSpec.scala
   :snippet: compressResponse-1
