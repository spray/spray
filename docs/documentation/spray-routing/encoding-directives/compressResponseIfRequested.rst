.. _-compressResponseIfRequested-:

compressResponseIfRequested
===========================

Only compresses the response when specifically requested by the ``Accept-Encoding`` request header
(i.e. the default is "no compression").

Signature
---------

::

    def compressResponseIfRequested()(implicit refFactory: ActorRefFactory): Directive0

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

The ``compressResponseIfRequested`` directive is an alias for ``compressResponse(NoEncoding, Gzip, Deflate)`` and will
behave as follows:

========================================= ===============================
``Accept-Encoding`` header                resulting response
========================================= ===============================
``Accept-Encoding: gzip``                 compressed with ``Gzip``
``Accept-Encoding: deflate``              compressed with ``Deflate``
``Accept-Encoding: deflate, gzip``        compressed with ``Gzip``
``Accept-Encoding: identity``             uncompressed
no ``Accept-Encoding`` header present     uncompressed
========================================= ===============================

For an overview of the different ``compressResponse`` directives see :ref:`WhenToUseWhichCompressResponseDirective`.

Example
-------

.. includecode:: ../code/docs/directives/EncodingDirectivesExamplesSpec.scala
   :snippet: compressResponseIfRequested
