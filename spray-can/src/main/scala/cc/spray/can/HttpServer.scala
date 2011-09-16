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
import HttpProtocols._
import java.util.concurrent.atomic.AtomicBoolean
import java.io.IOException
import akka.actor.PoisonPill
import java.net.InetAddress
import java.nio.ByteBuffer

/////////////////////////////////////////////
// HttpServer messages
////////////////////////////////////////////
case class RequestContext(
  request: HttpRequest,
  remoteAddress: InetAddress,
  responder: HttpResponse => Unit
)
case class Timeout(context: RequestContext)

/////////////////////////////////////////////
// HttpServer
////////////////////////////////////////////

object HttpServer {
  private case class Respond(conn: ServerConnection, buffers: List[ByteBuffer], closeAfterWrite: Boolean,
                             requestRecord: RequestRecord = null)
  private case class RequestRecord(request: HttpRequest, remoteAddress: InetAddress,
                                   timeoutResponder: HttpResponse => Unit) extends LinkedList.Element[RequestRecord]

  private[can] class ServerConnection(key: SelectionKey) extends Connection[ServerConnection](key) {
    var closeAfterWrite = false
    key.attach(this)
  }
}

final class HttpServer(val config: ServerConfig = ServerConfig.fromAkkaConf) extends HttpPeer with ResponsePreparer {
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
    case r: Respond => if (r.conn.writeBuffers.isEmpty) {
      prepareWriting(r)
    } else self ! r // we still have a previous write ongoing, so try again later
  }

  protected def handleConnectionEvent(key: SelectionKey) {
    if (key.isAcceptable) {
      log.debug("Accepting new connection")
      protectIO("Accept") {
        val socketChannel = serverSocketChannel.accept
        socketChannel.configureBlocking(false)
        val key = socketChannel.register(selector, SelectionKey.OP_READ) // start out only reading
        connections += new ServerConnection(key)
        log.debug("New connection accepted and registered")
      }
    } else throw new IllegalStateException
  }

  protected def handleMessageParsingComplete(conn: Conn, parser: CompleteMessageParser) {
    import parser._
    val requestLine = messageLine.asInstanceOf[RequestLine]
    import requestLine._
    log.debug("Dispatching {} request to '{}' to the service actor", method, uri)
    val alreadyCompleted = new AtomicBoolean(false)

    def respond(response: HttpResponse, requestRecord: RequestRecord): Boolean = {
      HttpResponse.verify(response)
      if (alreadyCompleted.compareAndSet(false, true)) {
        log.debug("Enqueueing valid HttpResponse as raw response")
        val (buffers, close) = prepare(response, protocol, connectionHeader)
        self ! Respond(conn, buffers, close, requestRecord)
        true
      } else false
    }

    val httpRequest = HttpRequest(method, uri, headers, body, protocol)
    val remoteAddress = conn.key.channel.asInstanceOf[SocketChannel].socket.getInetAddress
    var requestRecord: RequestRecord = null
    if (requestTimeoutCycle.isDefined) {
      requestRecord = new RequestRecord(
        request = httpRequest,
        remoteAddress = remoteAddress,
        timeoutResponder = { response => if (respond(response, requestRecord)) requestsTimedOut += 1 }
      )
      openRequests += requestRecord;
    }
    val responder: HttpResponse => Unit = { response =>
      if (!respond(response, requestRecord))
        log.warn("Received an additional response for an already completed request to '{}', ignoring...", httpRequest.uri)
    }
    serviceActor ! RequestContext(httpRequest, remoteAddress, responder)
    requestsDispatched += 1
    conn.messageParser = EmptyRequestParser // switch back to parsing the next request from the start
  }

  protected def handleMessageParsingError(conn: Conn, parser: ErrorMessageParser) {
    log.debug("Illegal request, responding with status {} and '{}'", parser.status, parser.message)
    val response = HttpResponse(status = parser.status,
      headers = List(HttpHeader("Content-Type", "text/plain"))).withBody(parser.message)
    // In case of a request parsing error we probably stopped reading the request somewhere in between, where we
    // cannot simply resume. Resetting to a known state is not easy either, so we need to close the connection to do so.
    // This is done here by pretending the request contained a "Connection: close" header
    val (buffers, close) = prepare(response, `HTTP/1.1`, Some("close"))
    self ! Respond(conn, buffers, close)
  }

  protected def finishWrite(conn: Conn) {
    if (conn.writeBuffers.isEmpty) {
      if (conn.closeAfterWrite) {
        close(conn)
      } else {
        conn.disableWriting() // done writing
      }
    }
  }

  protected def handleTimedOutRequests() {
    openRequests.forAllTimedOut(config.requestTimeout) { ctx =>
      log.warn("A request to '{}' timed out, dispatching to the TimeoutActor '{}'",
        ctx.request.uri, config.timeoutActorId)
      openRequests -= ctx
      openTimeouts += ctx
      import ctx._
      timeoutActor ! Timeout(RequestContext(request, remoteAddress, timeoutResponder))
    }
    openTimeouts.forAllTimedOut(config.timeoutTimeout) { ctx =>
      log.warn("The TimeoutService for '{}' timed out as well, responding with the static error reponse", ctx.request.uri)
      ctx.timeoutResponder(timeoutTimeoutResponse(ctx.request))
    }
  }

  protected def prepareWriting(respond: Respond) {
    import respond._
    if (conn.key.isValid) {
      log.debug("Received raw response, scheduling write")
      conn.writeBuffers = buffers
      conn.closeAfterWrite = closeAfterWrite
      conn.enableWriting()
    } else log.warn("Dropping response due to closed connection")
    if (requestRecord != null)
      requestRecord.memberOf -= requestRecord // remove from either the openRequests or the openTimeouts list
  }

  protected def timeoutTimeoutResponse(request: HttpRequest) = {
    HttpResponse(status = 500, headers = List(HttpHeader("Content-Type", "text/plain"))).withBody {
      "Ooops! The server was not able to produce a timely response to your request.\n" +
      "Please try again in a short while!"
    }
  }

  protected def openRequestCount = openRequests.size

  protected def serverHeader = config.serverHeader
}