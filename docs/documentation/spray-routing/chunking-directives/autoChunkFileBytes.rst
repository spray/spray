.. _-autoChunkFileBytes-:

autoChunkFileBytes
==================

Converts unchunked responses coming back from its inner route into chunked responses of which each chunk
is smaller or equal to the given size if the response entity is at least as large as the given threshold and contains
only ``HttpData.FileBytes``.

Signature
---------

::

    def autoChunkFileBytes(maxChunkSize: Long)(implicit factory: ActorRefFactory): Directive0
    def autoChunkFileBytes(threshold: Long, maxChunkSize: Long)(implicit factory: ActorRefFactory): Directive0

The signature shown is simplified, the real signature uses magnets. [1]_

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/


Description
-----------

See :ref:`-autoChunk-` for a more detailed description of the parameters as this directive is basically the same
with the added restriction to chunk only entities completely backed by files.


Example
-------

.. includecode:: ../code/docs/directives/ChunkingDirectivesExamplesSpec.scala
   :snippet: autoChunkFileBytes
