Response Streaming
==================

Apart from completing requests with simple ``HttpResponse`` instances *spray-routing* also supports asynchronous
response streaming. If you run *spray-routing* on top of the :ref:`spray-can` :ref:`HttpServer` a response stream can be
rendered as an HTTP/1.1 ``chunked`` response or, if ``chunkless-streaming`` is enabled, as a single response, whose
entity body is sent in several parts, one by one, across the network.

When running *spray-routing* on top of :ref:`spray-servlet` the exact interpretation of the individual response chunks
depends on the servlet container implementation (see the :ref:`spray-servlet` docs for more info on this).

A streaming response is started by sending a ``ChunkedResponseStart`` message to the ``responder`` of the
``RequestContext``. Afterwards the ``responder`` is ready to receive a number of ``MessageChunk`` messages. A streaming
response is terminated with a ``ChunkedMessageEnd`` message.

In order to not flood the network with chunks that it might not be able to currently digest it's always a good idea to
not send out another chunk before having received a ``spray.util.model.IOSent`` confirmation message from the
underlying layer.

The :ref:`Complete Examples` both contain sample code, which shows how to send a streaming response that is "pulled"
by the network via send confirmation messages.

