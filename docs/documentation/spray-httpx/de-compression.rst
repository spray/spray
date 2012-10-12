(De)compression
===============

The `HTTP spec`_ defines a ``Content-Encoding`` header, which signifies whether the entity body of an HTTP message is
"encoded" and, if so, by which algorithm. The only commonly used content encodings, apart from ``identity`` (i.e. plain
text), are compression algorithms.

Currently *spray* supports the compression and decompression of HTTP requests and responses with the ``gzip`` or
``deflate`` encodings. The core logic for this, which is shared by the :ref:`spray-client` and :ref:`spray-routing`
modules for the client- and server-side (respectively), lives in the `spray.httpx.encoding`_ package.

.. _HTTP spec: http://www.w3.org/Protocols/rfc2616/rfc2616.html
.. _spray.httpx.encoding: https://github.com/spray/spray/tree/master/spray-httpx/src/main/scala/spray/httpx/encoding


Compression of Chunk Streams
----------------------------

Properly combining HTTP compression with the ``chunked`` HTTP/1.1 Transfer-Encoding can be a little tricky.
For optimal results the peer sending the message (i.e. the client or the server) should use a single compression context
across all chunks, so that common patterns shared by several chunks contribute to a high compression ratio.
At the same time the decompressor at the other end must be able to properly decompress each chunk as it arrives.

In order to achieve this the compressor must properly flush its compression stream after each chunk, something that
the GZIP- and DeflaterOutputStream implementations of the Java 6 JDK unfortunately do not support correctly
(see `this JDK bug`__, fixed only in Java 7). *sprays* compression implementation jumps through a few hoops to achieve
the desired behavior also under Java 6, with no cost to you as the user.

__ http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4813885