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
import java.nio.channels.{SocketChannel, SelectionKey, ServerSocketChannel}
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import annotation.tailrec
import java.util.concurrent.atomic.AtomicInteger
import akka.actor.{ActorRef, Actor, PoisonPill}
import HttpProtocols._

/////////////////////////////////////////////
// HttpServer messages
////////////////////////////////////////////

/**
 * The [[cc.spray.can.HttpServer]] dispatches a `RequestContext` instance to the service actor (as configured in the
 * [[cc.spray.can.ServerConfig]] of the [[cc.spray.can.HttpServer]]) upon successful reception of an HTTP request.
 * The service actor is expected to either complete the request by calling `responder.complete` or start a chunked
 * response by calling `responder.startChunkedResponse`. If neither of this happens within the timeout period configured
 * as `requestTimeout` in the [[cc.spray.can.ServerConfig]] the [[cc.spray.can.HttpServer]] actor dispatches a
 * [[cc.spray.can.Timeout]] instance to the configured timeout actor.
 */
case class RequestContext(
  request: HttpRequest,
  remoteAddress: InetAddress,
  responder: RequestResponder
)

/**
 * When the service actor does not reply to a dispatched [[cc.spray.can.RequestContext]] within the time period
 * configured as `requestTimeout` in the [[cc.spray.can.ServerConfig]] the [[cc.spray.can.HttpServer]] dispatches
 * a `Timeout` instance to the timeout actor (as configured in the [[cc.spray.can.ServerConfig]]).
 */
case class Timeout(
  method: HttpMethod,
  uri: String,
  protocol: HttpProtocol,
  headers: List[HttpHeader],
  remoteAddress: InetAddress,
  complete: HttpResponse => Unit
)

/**
 * An instance of this trait is used by the application to complete incoming requests.
 */
trait RequestResponder {
  /**
   * Completes a request by responding with the given [[cc.spray.can.HttpResponse]]. Only the first invocation of
   * this method determines the response that is sent back to the client. All potentially following calls will trigger
   * an exception.
   */
  def complete(response: HttpResponse)

  /**
   * Starts a chunked (streaming) response. The given [[cc.spray.can.HttpResponse]] object must have the protocol
   * `HTTP/1.1` and is allowed to contain an entity body. Should the body of the given `HttpResponse` be non-empty it
   * is sent immediately following the responses HTTP header section as the first chunk.
   * The application is required to use the returned [[cc.spray.can.ChunkedResponder]] instance to send any number of
   * response chunks before calling the `ChunkedResponder`s `close` method to finalize the response.
   */
  def startChunkedResponse(response: HttpResponse): ChunkedResponder
}

/**
 * A `ChunkedResponder` is returned by the `startChunkedResponse` method of a [[cc.spray.can.RequestResponder]]
 * (the `responder` member of a [[cc.spray.can.RequestContext]]). It is used by the application to send the chunks and
 * finalization of a chunked (streaming) HTTP response.
 */
trait ChunkedResponder {
  /**
   * Send the given [[cc.spray.can.MessageChunk]] back to the client.
   */
  def sendChunk(chunk: MessageChunk)

  /**
   * Finalizes the chunked (streaming) response.
   */
  def close(extensions: List[ChunkExtension] = Nil, trailer: List[HttpHeader] = Nil)
}

/////////////////////////////////////////////
// HttpServer
////////////////////////////////////////////

object HttpServer {
  private[can] class Respond(
    val conn: ServerConnection,
    val buffers: List[ByteBuffer],
    val closeAfterWrite: Boolean,
    val responseNr: Int,
    val increaseResponseNr: Boolean = true,
    val requestRecord: RequestRecord = null) {
    var next: Respond = _
  }
  private[can] case class RequestRecord(method: HttpMethod, uri: String, protocol: HttpProtocol,
                                        headers: List[HttpHeader], remoteAddress: InetAddress,
                                        responder: RequestResponder) extends LinkedList.Element[RequestRecord]

  private[can] case class ChunkingContext(requestLine: RequestLine, headers: List[HttpHeader],
                                          connectionHeader: Option[String], streamActor: ActorRef)

  private[can] class ServerConnection(key: SelectionKey, emptyRequestParser: EmptyRequestParser)
          extends Connection[ServerConnection](key) {
    var requestsDispatched = 0
    var responseNr = 0
    var currentRespond: Respond = _
    var queuedResponds: Respond = _
    var chunkingContext: ChunkingContext = _
    key.attach(this)
    messageParser = emptyRequestParser

    def enqueue(respond: Respond) {
      @tailrec def insertAfter(cursor: Respond) {
        if (cursor.next == null) cursor.next = respond
        else if (cursor.next.responseNr <= respond.responseNr) insertAfter(cursor.next)
        else { respond.next = cursor.next; cursor.next = respond }
      }
      if (queuedResponds == null) queuedResponds = respond
      else if (queuedResponds.responseNr <= respond.responseNr) insertAfter(queuedResponds)
      else { respond.next = queuedResponds; queuedResponds = respond }
    }

    def countDispatch() = {
      val nextResponseNr = requestsDispatched
      requestsDispatched += 1
      nextResponseNr
    }

    def closeChunkingContext() {
      if (chunkingContext != null) {
        chunkingContext.streamActor ! PoisonPill // stop the stream actor
        chunkingContext = null // free for GC
      }
    }
  }
}

/**
 * The actor implementing the ''spray-can'' HTTP server functionality.
 * An `HttpServer` instance starts one private thread and binds to one port (as configured with the given
 * [[cc.spray.can.ServerConfig]]. It manages connections and requests quite efficiently and can handle thousands of
 * concurrent connections. For every incoming HTTP request the `HttpServer` creates an [[cc.spray.can.RequestContext]]
 * instance that is dispatched to the server actor configured via the given [[cc.spray.can.ServerConfig]].
 *
 * The service actor is expected to either complete the request by calling `responder.complete` or start a chunked
 * response by calling `responder.startChunkedResponse`. If neither of this happens within the timeout period configured
 * as `requestTimeout` in the [[cc.spray.can.ServerConfig]] the `HttpServer` actor dispatches a
 * [[cc.spray.can.Timeout]] instance to the configure timeout actor. The timeout actor is expected to complete the
 * request within the configured `timeoutTimeout` period. If this doesn't happen the `HttpServer` completes the request
 * itself with the result of its `timeoutTimeoutResponse` method.
 *
 * An `HttpServer` also reacts to [[cc.spray.can.GetStats]] messages.
 */
class HttpServer(val config: ServerConfig = ServerConfig.fromAkkaConf)
        extends HttpPeer("spray-can-server") with ResponsePreparer {
  import HttpServer._

  private lazy val log = LoggerFactory.getLogger(getClass)
  private lazy val serverSocketChannel = make(ServerSocketChannel.open) { channel =>
    channel.configureBlocking(false)
    channel.socket.bind(config.endpoint)
    channel.register(selector, SelectionKey.OP_ACCEPT)
  }
  private lazy val serviceActor = actor(config.serviceActorId)
  private lazy val timeoutActor = actor(config.timeoutActorId)
  private val openRequests = new LinkedList[RequestRecord]
  private val openTimeouts = new LinkedList[RequestRecord]

  private[can] type Conn = ServerConnection

  private val StartRequestParser = new EmptyRequestParser(config.parserConfig)
  private lazy val streamActorCreator = config.streamActorCreator.getOrElse {
    BufferingRequestStreamActor.creator(serviceActor, config.parserConfig.maxContentLength)
  }

  self.id = config.serverActorId

  override def preStart() {
    log.info("Starting spray-can HTTP server on {}", config.endpoint)
    try {
      serverSocketChannel // trigger serverSocketChannel initialization and registration
      super.preStart()
    } catch {
      case e: IOException => {
        log.error("Could not open and register server socket on {}:\n{}", config.endpoint, e.toString)
        self ! PoisonPill
      }
    }
  }

  override def postStop() {
    log.info("Stopped spray-can HTTP server on {}", config.endpoint)
    super.postStop()
  }

  protected override def cleanUp() {
    super.cleanUp()
    serverSocketChannel.close()
  }

  protected override def receive = super.receive orElse {
    case respond: Respond => {
      import respond._
      if (conn.writeBuffers.isEmpty && conn.responseNr == responseNr) {
        // we can only prepare the next write if we are not already writing and it's the right response
        prepareWriting(respond)
      } else conn.enqueue(respond)
    }
  }

  protected def handleConnectionEvent(key: SelectionKey) {
    if (key.isAcceptable) {
      log.debug("Accepting new connection")
      protectIO("Accept") {
        val socketChannel = serverSocketChannel.accept
        socketChannel.configureBlocking(false)
        val key = socketChannel.register(selector, SelectionKey.OP_READ) // start out only reading
        connections += new ServerConnection(key, StartRequestParser)
        log.debug("New connection accepted and registered")
      }
    } else throw new IllegalStateException
  }

  protected def prepareWriting(respond: Respond) {
    import respond._
    if (conn.key.isValid) {
      log.debug("Received raw response, scheduling write")
      conn.writeBuffers = buffers
      conn.currentRespond = respond
      conn.enableWriting()
    } else log.warn("Dropping response due to closed connection")
    if (requestRecord != null)
      requestRecord.memberOf -= requestRecord // remove from either the openRequests or the openTimeouts list
  }

  protected def finishWrite(conn: Conn) {
    if (conn.writeBuffers.isEmpty) {
      if (conn.currentRespond.closeAfterWrite) {
        close(conn)
      } else {
        if (conn.currentRespond.increaseResponseNr) {
          // we completely finished the response, i.e. either the HttpResponse has completely been written or
          // the last chunk of a chunked response has gone out
          conn.responseNr += 1
          conn.closeChunkingContext()
        }
        val next = conn.queuedResponds
        if (next != null && next.responseNr == conn.responseNr) {
          conn.queuedResponds = next.next
          prepareWriting(next)
        } else conn.disableWriting()
      }
    }
  }

  protected def handleCompleteMessage(conn: Conn, parser: CompleteMessageParser) {
    val requestLine = parser.messageLine.asInstanceOf[RequestLine]
    import requestLine._
    log.debug("Dispatching {} request to '{}' to the service actor", method, uri)
    val remoteAddress = conn.key.channel.asInstanceOf[SocketChannel].socket.getInetAddress
    val responder = createAndRegisterRequestResponder(conn, requestLine, parser.headers, remoteAddress, parser.connectionHeader)
    val request = HttpRequest(method, uri, parser.headers, parser.body, protocol)
    serviceActor ! RequestContext(request, remoteAddress, responder)
    conn.messageParser = StartRequestParser // switch back to parsing the next request from the start
    requestsDispatched += 1
  }

  protected def handleChunkedStart(conn: Conn, parser: ChunkedStartParser) {
    import parser._
    val requestLine = messageLine.asInstanceOf[RequestLine]
    import requestLine._
    val remoteAddress = conn.key.channel.asInstanceOf[SocketChannel].socket.getInetAddress
    log.debug("Dispatching start of chunked {} request to '{}' to a new stream actor", method, uri)
    val streamActor = Actor.actorOf(
      streamActorCreator(ChunkedRequestContext(HttpRequest(method, uri, headers), remoteAddress))
    ).start()
    conn.chunkingContext = ChunkingContext(requestLine, headers, connectionHeader, streamActor)
    conn.messageParser = new ChunkParser(config.parserConfig)
  }

  protected def handleChunkedChunk(conn: Conn, parser: ChunkedChunkParser) {
    conn.chunkingContext.streamActor ! MessageChunk(parser.extensions, parser.body)
    conn.messageParser = new ChunkParser(config.parserConfig)
  }

  protected def handleChunkedEnd(conn: Conn, parser: ChunkedEndParser) {
    val cc = conn.chunkingContext
    val remoteAddress = conn.key.channel.asInstanceOf[SocketChannel].socket.getInetAddress
    val responder = createAndRegisterRequestResponder(conn, cc.requestLine, cc.headers, remoteAddress, cc.connectionHeader)
    cc.streamActor ! ChunkedRequestEnd(parser.extensions, parser.trailer, responder)
    conn.messageParser = StartRequestParser // switch back to parsing the next request from the start
    requestsDispatched += 1
  }

  private def createAndRegisterRequestResponder(conn: Conn, requestLine: RequestLine, headers: List[HttpHeader],
                                                remoteAddress: InetAddress, connectionHeader: Option[String]) = {
    val responder = new DefaultRequestResponder(conn, requestLine, conn.countDispatch(), connectionHeader)
    if (requestTimeoutCycle.isDefined) {
      import requestLine._
      val requestRecord = RequestRecord(method, uri, protocol, headers, remoteAddress, responder)
      responder.setRequestRecord(requestRecord)
      openRequests += requestRecord;
    }
    responder
  }

  protected def handleParseError(conn: Conn, parser: ErrorParser) {
    log.warn("Illegal request, responding with status {} and '{}'", parser.status, parser.message)
    val response = HttpResponse(status = parser.status,
      headers = List(HttpHeader("Content-Type", "text/plain"))).withBody(parser.message)
    // In case of a request parsing error we probably stopped reading the request somewhere in between, where we
    // cannot simply resume. Resetting to a known state is not easy either, so we need to close the connection to do so.
    // This is done here by pretending the request contained a "Connection: close" header
    val (buffers, close) = prepareResponse(RequestLine(), response, Some("close"))
    conn.disableReading() // we can't read anymore on this connection
    self ! new Respond(conn, buffers, close, conn.countDispatch())
  }

  protected def handleTimedOutRequests() {
    openRequests.forAllTimedOut(config.requestTimeout) { record =>
      log.warn("A request to '{}' timed out, dispatching to the TimeoutActor '{}'", record.uri, config.timeoutActorId)
      openRequests -= record
      openTimeouts += record
      import record._
      timeoutActor ! Timeout(method, uri, protocol, headers, remoteAddress,
        responder.asInstanceOf[DefaultRequestResponder].timeoutResponder)
    }
    openTimeouts.forAllTimedOut(config.timeoutTimeout) { record =>
      import record._
      log.warn("The TimeoutService for '{}' timed out as well, responding with the static error reponse", uri)
      record.responder.asInstanceOf[DefaultRequestResponder].timeoutResponder {
        timeoutTimeoutResponse(method, uri, protocol, headers, remoteAddress)
      }
    }
  }

  /**
   * This methods determines the [[cc.spray.can.HttpResponse]] to sent back to the client if both the service actor
   * as well as the timeout actor do not produce timely responses with regard to their timeout periods configured
   * via the `HttpServers` [[cc.spray.can.ServerConfig]].
   */
  protected def timeoutTimeoutResponse(method: HttpMethod, uri: String, protocol: HttpProtocol,
                                       headers: List[HttpHeader], remoteAddress: InetAddress): HttpResponse = {
    HttpResponse(status = 500, headers = List(HttpHeader("Content-Type", "text/plain"))).withBody {
      "Ooops! The server was not able to produce a timely response to your request.\n" +
              "Please try again in a short while!"
    }
  }

  override protected def close(conn: Conn) {
    conn.closeChunkingContext()
    super.close(conn)
  }

  protected def openRequestCount = openRequests.size

  protected def serverHeader = config.serverHeader

  private class DefaultRequestResponder(conn: Conn, requestLine: RequestLine, responseNr: Int,
                                        connectionHeader: Option[String])
          extends RequestResponder { original =>
    private val UNCOMPLETED = 0
    private val COMPLETED = 1
    private val STREAMING = 2
    private val mode = new AtomicInteger(UNCOMPLETED)
    private var requestRecord: RequestRecord = _

    def complete(response: HttpResponse) {
      if (!trySend(response)) mode.get match {
        case COMPLETED =>
          log.warn("Received an additional response for an already completed request to '{}', ignoring...", requestLine.uri)
        case STREAMING =>
          log.warn("Received a regular response for a request to '{}', " +
                   "that a chunked response has already been started/completed, ignoring...", requestLine.uri)
      }
    }

    private def trySend(response: HttpResponse) = {
      HttpResponse.verify(response)
      if (mode.compareAndSet(UNCOMPLETED, COMPLETED)) {
        log.debug("Enqueueing valid HttpResponse as raw response")
        val (buffers, close) = prepareResponse(requestLine, response, connectionHeader)
        self ! new Respond(conn, buffers, close, responseNr, true, requestRecord)
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
        self ! new Respond(conn, buffers, false, responseNr, false, requestRecord)
        if (requestLine.method != HttpMethods.HEAD) new DefaultChunkedResponder(close) else new ChunkedResponder {
          def sendChunk(chunk: MessageChunk) {}
          def close(extensions: List[ChunkExtension], trailer: List[HttpHeader]) {}
        }
      } else throw new IllegalStateException {
        mode.get match {
          case COMPLETED => "The chunked response cannot be started since this request to '" + requestLine.uri + "' has already been completed"
          case STREAMING => "A chunked response has already been started (and maybe completed) for this request to '" + requestLine.uri + "'"
        }
      }
    }

    lazy val timeoutResponder: HttpResponse => Unit = { response =>
      if (original.trySend(response)) requestsTimedOut += 1
    }

    def setRequestRecord(record: RequestRecord) { requestRecord = record }

    class DefaultChunkedResponder(closeAfterLastChunk: Boolean)
            extends ChunkedResponder {
      private var closed = false
      def sendChunk(chunk: MessageChunk) {
        synchronized {
          if (!closed) {
            log.debug("Enqueueing response chunk")
            self ! new Respond(conn, prepareChunk(chunk.extensions, chunk.body), false, responseNr, false)
          } else throw new RuntimeException("Cannot send MessageChunk after HTTP stream has been closed")
        }
      }
      def close(extensions: List[ChunkExtension], trailer: List[HttpHeader]) {
        Trailer.verify(trailer)
        synchronized {
          if (!closed) {
            log.debug("Enqueueing final response chunk")
            val buffers = prepareFinalChunk(extensions, trailer)
            self ! new Respond(conn, buffers, closeAfterLastChunk, responseNr)
            closed = true
          } else throw new RuntimeException("Cannot close an HTTP stream that has already been closed")
        }
      }
    }
  }
}