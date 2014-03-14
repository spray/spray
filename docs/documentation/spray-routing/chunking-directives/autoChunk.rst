.. _-autoChunk-:

autoChunk
=========

Converts unchunked responses coming back from its inner route into chunked responses of which each chunk
is smaller or equal to the given size if the response entity is at least as large as the given threshold.

Signature
---------

::

    def autoChunk(maxChunkSize: Long)(implicit factory: ActorRefFactory): Directive0
    def autoChunk(threshold: Long, maxChunkSize: Long)(implicit factory: ActorRefFactory): Directive0

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

The parameter of type ``ChunkSizeMagnet`` decides for which values of ``HttpData`` the directive should apply and
how to chunk the data. Predefined instances of ``ChunkSizeMagnet`` decide this on the basis of two
parameters, the threshold size and the chunk size (if only one number is supplied it is used for both values). The
threshold parameter decides from which size on an entity should be converted into a chunked request. The chunk size
parameter decides how big each chunk should be at most.

See the :ref:`-autoChunkFileBytes-` directive for an alternative that adds another restriction to chunk a response only
when it consists only of ``FileBytes``, i.e. it is completely backed by a file.

Auto chunking is especially effective in combination with encoding. Encoding (gzip, deflate) always encodes the complete
response part in one step. For big entities this can be a disadvantage especially when the data has to be read from a file
into JVM heap buffers. Auto chunking helps here because it produces a lazy stream of response chunks that can be encoded
one by one by an encoder so that only one chunk is loaded into the JVM heap at one time.


Example
-------

.. includecode:: ../code/docs/directives/ChunkingDirectivesExamplesSpec.scala
   :snippet: autoChunk-0
