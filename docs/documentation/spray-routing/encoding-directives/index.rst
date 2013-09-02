.. _EncodingDirectives:

EncodingDirectives
==================

.. toctree::
   :maxdepth: 1

   compressResponse
   compressResponseIfRequested
   compressResponseWith
   decodeRequest
   decompressRequest
   decompressRequestWith
   encodeResponse
   requestEncodedWith
   responseEncodingAccepted

.. _WhenToUseWhichCompressResponseDirective:

When to use which compressResponse directive?
---------------------------------------------
There are three different versions of the ``compressResponse`` directive with
slightly different behavior for choosing when to compress the response.

You can use the table below to decide which directive to use.

====================================== ===================================================================
directive                              behavior
====================================== ===================================================================
:ref:`-compressResponse-`              Always compresses the response unless specifically requested not to
                                       by the ``Accept-Encoding: identity`` request header
:ref:`-compressResponseWith-`          Always compresses the response
:ref:`-compressResponseIfRequested-`   Only compresses the response when specifically requested by the
                                       ``Accept-Encoding`` request header
====================================== ===================================================================

See the individual directives for more detailed usage examples.

.. _WhenToUseWhichDecompressRequestDirective:

When to use which decompressRequest directive?
----------------------------------------------
There are two different versions of the ``decompressRequest`` directive with the main difference
being whether to try all possible decompression codecs, including ``NoEncoding``, or only a
specified subset of them.

You can use the table below to decide which directive to use.

============================== ===================================================================
directive                      behavior
============================== ===================================================================
:ref:`-decompressRequest-`     will try to decompress the request with either ``Gzip``,
                               ``Deflate``, or ``NoEncoding``, assuming the latter if no
                               ``Content-Encoding`` header is present.
:ref:`-decompressRequestWith-` will only try the specified Codecs in order, rejecting the request
                               if no matching ``Content-Encoding`` header is found.
============================== ===================================================================

See the individual directives for more detailed usage examples.

Combining compression and decompression
---------------------------------------

As with all Spray directives, the above single directives can be combined
using ``&`` to produce compound directives that will decompress requests and
compress responses in whatever combination required. Some examples:

.. includecode:: /../spray-routing-tests/src/test/scala/spray/routing/EncodingDirectivesSpec.scala
   :snippet: decompress-compress-combination-example
