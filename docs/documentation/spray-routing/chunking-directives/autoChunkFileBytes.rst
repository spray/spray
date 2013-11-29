.. _-autoChunkFileBytes-:

autoChunkFileBytes
==================

Converts unchunked responses coming back from its inner route into chunked responses of which each chunk
is smaller or equal to the given size if the response entity is at least as large as the given threshold and contains
only ``HttpData.FileBytes``.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/ChunkingDirectives.scala
   :snippet: autoChunkFileBytes

Description
-----------

See :ref:`-autoChunk-` for a more detailed description of the parameters as this directive is basically the same
with the added restriction to chunk only entities completely backed by files.


Example
-------

.. includecode:: ../code/docs/directives/ChunkingDirectivesExamplesSpec.scala
   :snippet: autoChunkFileBytes
