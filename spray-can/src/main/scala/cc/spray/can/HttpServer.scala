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
import utils.LinkedList
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import annotation.tailrec
import akka.actor.{Actor, PoisonPill}
import java.util.concurrent.atomic.{AtomicInteger, AtomicBoolean}

/////////////////////////////////////////////
// HttpServer messages
////////////////////////////////////////////
case class RequestContext(
  request: HttpRequest,
  remoteAddress: InetAddress,
  responder: RequestResponder
)

case class Timeout(
  request: HttpRequest,
  remoteAddress: InetAddress,
  complete: HttpResponse => Unit
)

trait RequestResponder {
  def complete(response: HttpResponse)
  def startStreaming(responseStart: ChunkedResponseStart): StreamHandler
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
  private[can] case class RequestRecord(request: HttpRequest, remoteAddress: InetAddress,
                                        responder: RequestResponder) extends LinkedList.Element[RequestRecord]

  private[can] class ServerConnection(key: SelectionKey, emptyRequestParser: EmptyRequestParser)
          extends Connection[ServerConnection](key) {
    var requestsDispatched = 0
    var responseNr = 0
    var currentRespond: Respond = _
    var queuedResponds: Respond = _
    key.attach(this)
    messageParser = emptyRequestParser

    def enqueue(respond: Respond) {
      @tailrec def insertAfter(cursor: Respond) {
        if (cursor.next == null) cursor.next = respond
        else if (cursor.next.responseNr < respond.responseNr) insertAfter(cursor.next)
        else { respond.next = cursor.next; cursor.next = respond }
      }
      if (queuedResponds == null) queuedResponds = respond
      else if (queuedResponds.responseNr < respond.responseNr) insertAfter(queuedResponds)
      else { respond.next = queuedResponds; queuedResponds = respond }
    }

    def countDispatch() = {
      val nextResponseNr = requestsDispatched
      requestsDispatched += 1
      nextResponseNr
    }
  }
}

class HttpServer(val config: ServerConfig = ServerConfig.fromAkkaConf) extends HttpPeer with ResponsePreparer {
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
    new BufferingRequestStreamActorCreator(serviceActor, config.parserConfig.maxContentLength)
  }

  self.id = config.serverActorId

  // we use our own custom single-thread dispatcher, because our thread will, for the most time,
  // be blocked at selector selection, therefore we need to wake it up upon message or task arrival
  self.dispatcher = new SelectorWakingDispatcher("spray-can-server", selector)

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
        if (conn.currentRespond.increaseResponseNr) conn.responseNr += 1
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
    val request = HttpRequest(method, uri, parser.headers, parser.body, protocol)
    val responder = new DefaultRequestResponder(conn, requestLine, conn.countDispatch(), parser.connectionHeader)
    val remoteAddress = conn.key.channel.asInstanceOf[SocketChannel].socket.getInetAddress
    if (requestTimeoutCycle.isDefined) {
      val requestRecord = RequestRecord(request, remoteAddress, responder)
      responder.setRequestRecord(requestRecord)
      openRequests += requestRecord;
    }
    requestsDispatched += 1
    conn.messageParser = StartRequestParser // switch back to parsing the next request from the start
    serviceActor ! RequestContext(request, remoteAddress, responder)
  }

  protected def handleChunkedStart(conn: Conn, parser: ChunkedStartParser) {
    val requestLine = parser.messageLine.asInstanceOf[RequestLine]
    import requestLine._
    val remoteAddress = conn.key.channel.asInstanceOf[SocketChannel].socket.getInetAddress
    log.debug("Dispatching start of chunked {} request to '{}' to a new stream actor", method, uri)
    val streamActor = Actor.actorOf(
      streamActorCreator(ChunkedRequestContext(ChunkedRequestStart(method, uri, parser.headers), remoteAddress))
    ).start()
    conn.messageParser = new ChunkParser(config.parserConfig,
      RequestChunkingContext(requestLine, parser.connectionHeader, streamActor))
  }

  protected def handleChunkedEnd(conn: Conn, parser: ChunkedEndParser) {
    val context = parser.context.asInstanceOf[RequestChunkingContext]
    import context._
    val responder = new DefaultRequestResponder(conn, requestLine, conn.countDispatch(), connectionHeader)
    streamActor ! ChunkedRequestEnd(parser.extensions, parser.trailer, responder)
    conn.messageParser = StartRequestParser // switch back to parsing the next request from the start
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
    openRequests.forAllTimedOut(config.requestTimeout) { ctx =>
      log.warn("A request to '{}' timed out, dispatching to the TimeoutActor '{}'",
        ctx.request.uri, config.timeoutActorId)
      openRequests -= ctx
      openTimeouts += ctx
      import ctx._
      timeoutActor ! Timeout(request, remoteAddress, responder.asInstanceOf[DefaultRequestResponder].timeoutResponder)
    }
    openTimeouts.forAllTimedOut(config.timeoutTimeout) { ctx =>
      log.warn("The TimeoutService for '{}' timed out as well, responding with the static error reponse", ctx.request.uri)
      ctx.responder.asInstanceOf[DefaultRequestResponder].timeoutResponder(timeoutTimeoutResponse(ctx.request))
    }
  }

  protected def timeoutTimeoutResponse(request: HttpRequest) = {
    HttpResponse(status = 500, headers = List(HttpHeader("Content-Type", "text/plain"))).withBody {
      "Ooops! The server was not able to produce a timely response to your request.\n" +
      "Please try again in a short while!"
    }
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
                   "that a streaming response has already been started/completed, ignoring...", requestLine.uri)
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

    def startStreaming(responseStart: ChunkedResponseStart) = {
      ChunkedResponseStart.verify(responseStart)
      if (mode.compareAndSet(UNCOMPLETED, STREAMING)) {
        log.debug("Enqueueing start of streaming response")
        val headers = HttpHeader("Transfer-Encoding", "chunked") :: responseStart.headers
        val (buffers, close) = prepareResponse(requestLine, HttpResponse(responseStart.status, headers), connectionHeader)
        self ! new Respond(conn, buffers, false, responseNr, false, requestRecord)
        new ResponseStreamHandler(responseStart, close)
      } else throw new IllegalStateException {
        mode.get match {
          case COMPLETED => "The streaming response cannot be started since this request to '" + requestLine.uri + "' has already been completed"
          case STREAMING => "A streaming response has already been started for this request to '" + requestLine.uri + "'"
        }
      }
    }

    lazy val timeoutResponder: HttpResponse => Unit = { response =>
      if (original.trySend(response)) requestsTimedOut += 1
    }

    def setRequestRecord(record: RequestRecord) { requestRecord = record }

    class ResponseStreamHandler(responseStart: ChunkedResponseStart, closeAfterLastChunk: Boolean)
            extends StreamHandler {
      private var closed = false
      def sendChunk(chunk: MessageChunk) {
        synchronized {
          if (!closed) {
            log.debug("Enqueueing streaming response chunk")
            self ! new Respond(conn, prepareResponseChunk(chunk), false, responseNr, false, null)
          } else throw new RuntimeException("Cannot send MessageChunk after HTTP stream has been closed")
        }
      }
      def closeStream(extensions: List[ChunkExtension], trailer: List[HttpHeader]) {
        if (!trailer.isEmpty) {
          require(trailer.forall(_.name != "Content-Length"), "Content-Length header is not allowed in trailer")
          require(trailer.forall(_.name != "Transfer-Encoding"), "Transfer-Encoding header is not allowed in trailer")
          require(trailer.forall(_.name != "Trailer"), "Trailer header is not allowed in trailer")
        }
        synchronized {
          if (!closed) {
            log.debug("Enqueueing final streaming response chunk")
            val buffers = prepareFinalResponseChunk(extensions, trailer)
            self ! new Respond(conn, buffers, closeAfterLastChunk, responseNr)
            closed = true
          } else throw new RuntimeException("Cannot close an HTTP stream that has already been closed")
        }
      }
    }
  }
}