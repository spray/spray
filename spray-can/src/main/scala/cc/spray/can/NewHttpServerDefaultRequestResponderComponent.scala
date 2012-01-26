package cc.spray.can

import java.util.concurrent.atomic.AtomicInteger
import model.HttpHeader

trait NewHttpServerDefaultRequestResponderComponent {

  class DefaultRequestResponder(requestLine: RequestLine, responseNr: Int, connectionHeader: Option[String],
                                requestRecord: => Option[requestRecords.Record]) extends RequestResponder {
    private val UNCOMPLETED = 0
    private val COMPLETED = 1
    private val STREAMING = 2
    private val mode = new AtomicInteger(UNCOMPLETED)

    def complete(response: HttpResponse) {
      if (!trySend(response)) mode.get match {
        case COMPLETED =>
          log.warn("Received an additional response for an already completed request to '{}', ignoring...", requestLine.uri)
        case STREAMING =>
          log.warn("Received a regular response for a request to '{}', " +
                   "that a chunked response has already been started/completed, ignoring...", requestLine.uri)
      }
    }

    def trySend(response: HttpResponse) = {
      HttpResponse.verify(response)
      if (mode.compareAndSet(UNCOMPLETED, COMPLETED)) {
        log.debug("Enqueueing valid HttpResponse as raw response")
        val (buffers, close) = prepareResponse(requestLine, response, connectionHeader)
        self ! new Respnd(buffers, close, responseNr, increaseResponseNr = true, requestRecord = requestRecord)
        true
      } else false
    }

    def startChunkedResponse(response: HttpResponse) = {
      HttpResponse.verify(response)
      require(response.protocol == `HTTP/1.1`, "Chunked responses must have protocol HTTP/1.1")
      require(requestLine.protocol == `HTTP/1.1`, "Cannot reply with a chunked response to an HTTP/1.0 client")
      if (mode.compareAndSet(UNCOMPLETED, STREAMING)) {
        log.debug("Enqueueing start of chunked response")
        val (buffers, close) = prepareChunkedResponseStart(requestLine, response, connectionHeader)
        self ! new Respnd(conn, buffers, closeAfterWrite = false, responseNr = responseNr,
          increaseResponseNr = false, requestRecord = requestRecord)
        if (requestLine.method == HttpMethods.HEAD) sys.error("Cannot respond to a HEAD request with a chunked response")
        new DefaultChunkedResponder(close, response.body.length > 0)
      } else throw new IllegalStateException {
        mode.get match {
          case COMPLETED => "The chunked response cannot be started since this request to '" + requestLine.uri + "' has already been completed"
          case STREAMING => "A chunked response has already been started (and maybe completed) for this request to '" + requestLine.uri + "'"
        }
      }
    }

    lazy val timeoutResponder: HttpResponse => Unit = { response =>
      if (trySend(response)) requestsTimedOut += 1
    }

    def resetConnectionTimeout() { self ! RefreshConnection(conn) }

    class DefaultChunkedResponder(closeAfterLastChunk: Boolean, initialChunkSent: Boolean) extends ChunkedResponder {
      private var closed = false

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

    } // DefaultChunkedResponder

  } // DefaultRequestResponder

}