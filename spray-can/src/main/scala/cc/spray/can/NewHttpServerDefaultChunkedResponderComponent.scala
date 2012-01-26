package cc.spray.can

import akka.dispatch.DefaultCompletableFuture

trait NewHttpServerDefaultChunkedResponderComponent {

  class DefaultChunkedResponder(closeAfterLastChunk: Boolean, initialChunkSent: Boolean) extends ChunkedResponder {
    var closed = false

    def sendChunk(chunk: MessageChunk) = {
      synchronized {
        if (!closed) {
          log.debug("Enqueueing response chunk")
          val sentFuture = new DefaultCompletableFuture[Unit](Long.MaxValue)
          self ! new Respnd(conn,
            buffers = prepareChunk(chunk.extensions, chunk.body),
            closeAfterWrite = false,
            responseNr = responseNr,
            increaseResponseNr = false,
            onSent = Some(sentFuture)
          )
          sentFuture
        } else throw new RuntimeException("Cannot send MessageChunk after HTTP stream has been closed")
      }
    }

    def close(extensions: List[ChunkExtension], trailer: List[HttpHeader]) {
      Trailer.verify(trailer)
      synchronized {
        if (!closed) {
          log.debug("Enqueueing final response chunk")
          val buffers = prepareFinalChunk(extensions, trailer)
          self ! new Respnd(conn, buffers, closeAfterLastChunk, responseNr)
          closed = true
        } else throw new RuntimeException("Cannot close an HTTP stream that has already been closed")
      }
    }
  }
}