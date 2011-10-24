/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.can

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.channels.{SocketChannel, SelectionKey}
import java.nio.ByteBuffer
import collection.mutable.Queue
import java.lang.IllegalStateException
import akka.dispatch.DefaultCompletableFuture
import akka.actor.{ActorRef, Actor, UntypedChannel}
import HttpProtocols._

/**
 * Message to be send to an [[cc.spray.can.HttpClient]] actor to initiate a new connection to the given host and port.
 * Upon successful establishment of the HTTP connection the [[cc.spray.can.HttpClient]] responds with an
 * [[cc.spray.can.HttpConnection]] instance.
 */
case class Connect(host: String, port: Int = 80)

object HttpClient extends HttpDialogComponent {
  private[can] class RequestMark // a unique object used to mark all parts of one chunked request
  private[can] case class Send(
    conn: ClientConnection,
    request: HttpRequest,
    buffers: List[ByteBuffer],
    commitMark: Option[RequestMark], // None for regular (unchunked) requests
    responder: Option[AnyRef => Unit] // Only defined for regular requests and final chunks of chunked requests
  )
  private case class Close(conn: ClientConnection)
  private case class RequestRecord(request: HttpRequest, conn: ClientConnection) extends LinkedList.Element[RequestRecord]
  private[can] abstract class PendingResponse(val request: HttpRequest) {
    def complete(response: AnyRef)
    def deliverPartialResponse(response: AnyRef)
  }
  private[can] class ClientConnection(key: SelectionKey, val host: String, val port: Int,
                                      val connectionResponseChannel: Option[UntypedChannel] = None)
          extends Connection[ClientConnection](key) {
    val pendingResponses = Queue.empty[PendingResponse]
    val requestQueue = Queue.empty[Send]
    var currentCommitMark: Option[RequestMark] = None // defined if a chunked request is currently being written
    key.attach(this)
    messageParser = UnexpectedResponseErrorParser
    def closeAllPendingWithError(error: String) { pendingResponses.foreach(_.complete(new HttpClientException(error))) }
    def resetParser(config: MessageParserConfig) {
      messageParser = {
        if (pendingResponses.isEmpty) UnexpectedResponseErrorParser
        else new EmptyResponseParser(config, pendingResponses.head.request.method)
      }
    }
  }
  private val UnexpectedResponseErrorParser = ErrorParser("Received unexpected HttpResponse")
}

/**
 * The actor implementing the ''spray-can'' HTTP client functionality.
 * Normally you only need to start one `HttpClient` actor per JVM instance since an `HttpClient` is able to concurrently
 * and efficiently manage a large number of connections.
 *
 * An `HttpClient` reacts to [[cc.spray.can.Connect]] and [[cc.spray.can.GetStats]] messages.
 */
class HttpClient(val config: ClientConfig = ClientConfig.fromAkkaConf) extends HttpPeer("spray-can-client") {
  import HttpClient._

  private lazy val log = LoggerFactory.getLogger(getClass)
  private val openRequests = new LinkedList[RequestRecord]

  private[can] type Conn = ClientConnection

  self.id = config.clientActorId

  override def preStart() {
    log.info("Starting spray-can HTTP client")
    super.preStart()
  }

  override def postStop() {
    log.info("Stopped spray-can HTTP client")
    super.postStop()
  }

  protected override def receive = super.receive orElse {
    case Connect(host, port) => initiateConnection(host, port)
    case send: Send => {
      import send.conn._
      // if writeBuffers aren't empty we are in the middle of a write, so we have to queue
      // otherwise we also have to queue if a chunked request is ongoing and the current Send is not part of it
      if (writeBuffers.isEmpty && (currentCommitMark.isEmpty || send.commitMark == currentCommitMark))
        prepareWriting(send)
      else
        requestQueue.enqueue(send)
    }
    case Close(conn) => close(conn)
  }

  private def initiateConnection(host: String, port: Int) {
    val address = new InetSocketAddress(host, port)
    log.debug("Initiating new connection to {}", address)
    protectIO("Init connect") {
      val socketChannel = SocketChannel.open()
      socketChannel.configureBlocking(false)
      if (socketChannel.connect(address)) {
        log.debug("New connection immediately established")
        val key = socketChannel.register(selector, SelectionKey.OP_READ) // start out with writing disabled
        val conn = new ClientConnection(key, host, port)
        connections += conn
        new DefaultHttpConnection(conn)
      } else {
        log.debug("Connection request registered")
        val key = socketChannel.register(selector, SelectionKey.OP_CONNECT)
        new ClientConnection(key, host, port, Some(self.channel))
      }
    } match {
      case Right(_: ClientConnection) => // nothing to do
      case Right(x: HttpConnection) => if (!self.tryReply(x)) log.error("Couldn't reply to Connect message")
      case Left(error) => self.channel.sendException {
        new HttpClientException("Could not connect to " + address + ": " + error)
      }
      case _ => throw new IllegalStateException
    }
  }

  protected def handleConnectionEvent(key: SelectionKey) {
    if (key.isConnectable) {
      val conn = key.attachment.asInstanceOf[ClientConnection]
      conn.connectionResponseChannel.foreach { channel =>
        protectIO("Connect", conn) {
          key.channel.asInstanceOf[SocketChannel].finishConnect()
          conn.key.interestOps(SelectionKey.OP_READ)
          connections += conn
          log.debug("Connected to {}:{}", conn.host, conn.port)
          new DefaultHttpConnection(conn)
        } match {
          case Right(x) =>  if (!channel.tryTell(x)) log.error("Couldn't reply to Connect message")
          case Left(error) => channel.sendException {
            new HttpClientException("Could not connect to " + conn.host + ':' + conn.port + " due to " + error)
          }
        }
      }
    } else throw new IllegalStateException
  }

  private def prepareWriting(send: Send) {
    import send._
    if (conn.key.isValid) {
      if (responder.isDefined) {
        log.debug {
          if (commitMark.isEmpty) "Received raw request, scheduling write" else "Received final chunk, scheduling write"
        }
        val requestRecord = RequestRecord(request, conn)
        conn.pendingResponses.enqueue(new PendingResponse(request) {
          def complete(response: AnyRef) {
            log.debug("Completing request with {}", response)
            responder.get.apply(response)
            openRequests -= requestRecord
          }
          def deliverPartialResponse(response: AnyRef) {
            log.debug("Delivering partial response: {}", response)
            responder.get.apply(response)
          }
        })
        openRequests += requestRecord
        requestsDispatched += 1
        conn.currentCommitMark = None // if we were in a chunked request it is now complete
      } else {
        log.debug("Received chunked request start or chunk, scheduling write")
        conn.currentCommitMark = commitMark // signal that we are now in a chunked request
      }
      conn.writeBuffers = buffers
      conn.enableWriting()
    } else {
      send.responder.map(_(new HttpClientException("Cannot send request due to closed connection")))
    }
  }

  protected def finishWrite(conn: ClientConnection) {
    import conn._
    if (writeBuffers.isEmpty) {
      if (messageParser == UnexpectedResponseErrorParser && !pendingResponses.isEmpty) {
        // if this is the first request (of a series) we need to re-initialize the response parser
        messageParser = new EmptyResponseParser(config.parserConfig, pendingResponses.head.request.method)
      }
      if (requestQueue.isEmpty) {
        disableWriting()
      } else conn.currentCommitMark match {
        case None => prepareWriting(requestQueue.dequeue())
        case mark => requestQueue.dequeueFirst(_.commitMark == mark).foreach(prepareWriting)
      }
    }
  }

  protected def handleCompleteMessage(conn: Conn, parser: CompleteMessageParser) {
    import parser._
    val statusLine = messageLine.asInstanceOf[StatusLine]
    import statusLine._
    val response = HttpResponse(status, headers, body, protocol)
    assert(!conn.pendingResponses.isEmpty)
    conn.pendingResponses.dequeue().complete(response)
    conn.resetParser(config.parserConfig)
  }

  protected def handleChunkedStart(conn: Conn, parser: ChunkedStartParser) {
    val statusLine = parser.messageLine.asInstanceOf[StatusLine]
    assert(!conn.pendingResponses.isEmpty)
    conn.pendingResponses.head.deliverPartialResponse(ChunkedResponseStart(statusLine.status, parser.headers))
    conn.messageParser = new ChunkParser(config.parserConfig)
  }

  protected def handleChunkedChunk(conn: Conn, parser: ChunkedChunkParser) {
    assert(!conn.pendingResponses.isEmpty)
    conn.pendingResponses.head.deliverPartialResponse(MessageChunk(parser.extensions, parser.body))
    conn.messageParser = new ChunkParser(config.parserConfig)
  }

  protected def handleChunkedEnd(conn: Conn, parser: ChunkedEndParser) {
    assert(!conn.pendingResponses.isEmpty)
    conn.pendingResponses.dequeue().complete(ChunkedResponseEnd(parser.extensions, parser.trailer))
    conn.resetParser(config.parserConfig)
  }

  protected def handleParseError(conn: Conn, parser: ErrorParser) {
    log.warn("Received illegal response: {}", parser.message)
    // In case of a response parsing error we probably stopped reading the response somewhere in between, where we
    // cannot simply resume. Resetting to a known state is not easy either, so we need to close the connection to do so.
    conn.closeAllPendingWithError(parser.message)
    close(conn)
  }

  override protected def cleanClose(conn: Conn) {
    conn.messageParser match {
      case x: ToCloseBodyParser => handleCompleteMessage(conn, x.complete)
      case _ =>
    }
    conn.closeAllPendingWithError("Server closed connection")
    super.cleanClose(conn)
  }

  protected def handleTimedOutRequests() {
    openRequests.forAllTimedOut(config.requestTimeout) { requestRecord =>
      import requestRecord._
      log.warn("{} request to '{}' timed out, closing the connection", request.method, request.uri)
      conn.closeAllPendingWithError("Request timed out")
      close(conn)
    }
  }

  override protected def reapConnection(conn: Conn) {
    conn.closeAllPendingWithError("Connection closed due to idle timeout")
    super.reapConnection(conn)
  }

  protected def openRequestCount = openRequests.size

  private class DefaultHttpConnection(conn: ClientConnection) extends HttpConnection with RequestPreparer {
    protected def userAgentHeader = config.userAgentHeader

    def send(request: HttpRequest) = {
      // we "disable" the akka future timeout, since we rely on our own logic
      val future = new DefaultCompletableFuture[HttpResponse](Long.MaxValue)
      val actor = Actor.actorOf(new DefaultReceiverActor(future, config.parserConfig.maxContentLength))
      sendAndReceive(request, actor.start())
      future
    }

    def sendAndReceive(request: HttpRequest, receiver: ActorRef, context: Option[Any] = None) {
      HttpRequest.verify(request)
      log.debug("Enqueueing valid HttpRequest as raw request")
      val buffers = prepareRequest(request, conn.host, conn.port)
      self ! Send(conn, request, buffers, None, Some(responder(receiver, context)))
    }

    def startChunkedRequest(request: HttpRequest) = {
      HttpRequest.verify(request)
      require(request.protocol == `HTTP/1.1`, "Chunked requests must have protocol HTTP/1.1")
      log.debug("Enqueueing start of chunked request")
      val mark = Some(new RequestMark)
      val buffers = prepareChunkedRequestStart(request, conn.host, conn.port)
      self ! Send(conn, request, buffers, mark, None)
      new DefaultChunkedRequester(request, mark)
    }

    def close() {
      self ! Close(conn)
    }

    private def responder(receiver: ActorRef, context: Option[Any]): AnyRef => Unit = {
      context match {
        case Some(ctx) => receiver ! (_, ctx)
        case None => receiver ! _
      }
    }

    class DefaultChunkedRequester(request: HttpRequest, mark: Option[RequestMark]) extends ChunkedRequester {
      private var closed = false
      def sendChunk(chunk: MessageChunk) {
        synchronized {
          if (!closed) {
            log.debug("Enqueueing request chunk")
            val buffers = prepareChunk(chunk.extensions, chunk.body)
            self ! Send(conn, request, buffers, mark, None)
          } else throw new RuntimeException("Cannot send MessageChunk after HTTP stream has been closed")
        }
      }

      def close(extensions: List[ChunkExtension], trailer: List[HttpHeader]) = {
        // we "disable" the akka future timeout, since we rely on our own logic
        val future = new DefaultCompletableFuture[HttpResponse](Long.MaxValue)
        val actor = Actor.actorOf(new DefaultReceiverActor(future, config.parserConfig.maxContentLength))
        closeAndReceive(actor.start(), None, extensions, trailer)
        future
      }

      def closeAndReceive(receiver: ActorRef, context: Option[Any], extensions: List[ChunkExtension],
                          trailer: List[HttpHeader]) {
        Trailer.verify(trailer)
        synchronized {
          if (!closed) {
            log.debug("Enqueueing final request chunk")
            val buffers = prepareFinalChunk(extensions, trailer)
            self ! Send(conn, request, buffers, mark, Some(responder(receiver, context)))
            closed = true
          } else throw new RuntimeException("Cannot close an HTTP stream that has already been closed")
        }
      }
    }
  }
}

/**
 * Special exception used for transporting error occuring during [[cc.spray.can.HttpClient]] operations.
 */
class HttpClientException(message: String) extends RuntimeException(message)