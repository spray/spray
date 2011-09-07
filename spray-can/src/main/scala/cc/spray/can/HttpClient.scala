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
import utils.LinkedList

sealed trait ConnectionHandle {
  private[can] def connRecord: ConnRecord
}

// public incoming messages
case object GetClientStats
case class Connect(host: String, port: Int = 80)
case class Send(connection: ConnectionHandle, request: HttpRequest)
case class Close(connection: ConnectionHandle)
//case class SendPooled(request: HttpRequest) // coming
// plus HttpRequest itself (coming)

// public outgoing messages
case class ConnectionResult(value: Either[String, ConnectionHandle]) // response to Connect
case class Received(value: Either[String, HttpResponse]) // response to Send

object HttpClient extends HighLevelHttpClient {
  private class ClientConnRecord(key: SelectionKey, load: ConnRecordLoad) extends ConnRecord(key, load) {
    var deliverResponse: Received => Unit = _
  }
  private case class TimeoutContext(request: HttpRequest, connRec: ClientConnRecord)
          extends LinkedList.Element[TimeoutContext]
}

class HttpClient(config: ClientConfig = AkkaConfClientConfig) extends HttpPeer(config) with RequestPreparer {
  import HttpClient._

  private lazy val log = LoggerFactory.getLogger(getClass)
  private val openRequests = new LinkedList[TimeoutContext]

  self.id = config.clientActorId

  // we use our own custom single-thread dispatcher, because our thread will, for the most time,
  // be blocked at selector selection, therefore we need to wake it up upon message or task arrival
  self.dispatcher = new SelectorWakingDispatcher("spray-can-client", selector)

  override def preStart() {
    log.info("Starting spray-can HTTP client")
    super.preStart()
  }

  override def postStop() {
    log.info("Stopped spray-can HTTP client")
    super.postStop()
  }

  protected override def receive = super.receive orElse {
    case Connect(host, port) => self reply ConnectionResult(initiateConnection(new InetSocketAddress(host, port)))
    case Send(connection, request) => send(connection.connRecord, request)
    case Close(connection) => close(connection.connRecord)
  }

  protected def initiateConnection(address: InetSocketAddress): Either[String, ConnectionHandle] = {
    log.debug("Initiating new connection to {}", address)
    protectIO("Init connect") {
      val socketChannel = SocketChannel.open()
      socketChannel.configureBlocking(false)
      val key = if (socketChannel.connect(address)) {
        log.debug("New connection immediately established")
        socketChannel.register(selector, SelectionKey.OP_READ) // start out in reading mode
      } else {
        log.debug("Connection request registered")
        socketChannel.register(selector, SelectionKey.OP_CONNECT)
      }
      val connRec = new ClientConnRecord(key, load = new EmptyResponseParser)
      key.attach(connRec)
      connections += connRec
      httpConnectionFor(connRec)
    }
  }

  protected def send(connRec: ConnRecord, request: HttpRequest) {
    def verifyRequest = {
      import request._
      if (method != null) {
        if (uri != null && !uri.isEmpty) {
          if (headers != null) {
            if (body != null) {
              if (headers.forall(_.name != "Content-Length")) {
                None
              } else Some("Content-Length header must not be present, the HttpClient sets it itself")
            } else Some("body must not be null (you can use cc.spray.can.EmptyByteArray for an empty body)")
          } else Some("headers must not be null")
        } else Some("uri must not be null or empty")
      } else Some("method must not be null")
    }

    if (connRec.key.isValid) {
      verifyRequest match {
        case None => {
          log.debug("Received valid HttpRequest to send, scheduling write")
          val clientConnRec = connRec.asInstanceOf[ClientConnRecord]
          clientConnRec.key.interestOps(SelectionKey.OP_WRITE)
          clientConnRec.load = WriteJob(buffers = prepare(request), closeConnection = false)
          val timeoutContext = TimeoutContext(request, clientConnRec)
          val responseChannel = self.channel
          clientConnRec.deliverResponse = { received =>
            openRequests -= timeoutContext
            responseChannel ! received
          }
          openRequests += timeoutContext
        }
        case Some(error) => self reply Received(Left("Illegal HttpRequest: " + error))
      }
    } else self reply Received(Left("Connection closed"))
  }

  protected def httpConnectionFor(connRec: ConnRecord) = new ConnectionHandle { val connRecord = connRec }

  protected def accept() {
    throw new IllegalStateException
  }

  protected def finishConnection(connRec: ConnRecord) {
    protectIO("Finish connect", connRec) {
      val socketChannel = connRec.key.channel.asInstanceOf[SocketChannel]
      socketChannel.finishConnect()
      connRec.key.interestOps(SelectionKey.OP_READ) // start out in reading mode
    }
  }

  protected def readComplete(connRec: ConnRecord, parser: CompleteMessageParser) = {
    import parser._
    val statusLine = messageLine.asInstanceOf[StatusLine]
    import statusLine._
    val response = HttpResponse(status, headers, body, protocol)
    connRec.asInstanceOf[ClientConnRecord].deliverResponse(Received(Right(response)))
    new EmptyResponseParser
  }

  protected def readParsingError(connRec: ConnRecord, parser: ErrorMessageParser) = {
    connRec.asInstanceOf[ClientConnRecord].deliverResponse(Received(Left(parser.message)))
    self ! Close(httpConnectionFor(connRec))
    parser // we just need to return some parser,
  }        // it will never be used since we close the connection after writing the error response

  protected def writeComplete(connRec: ConnRecord) = {
    requestsDispatched += 1
    new EmptyResponseParser
  }

  protected def handleTimedOutRequests() {
    openRequests.forAllTimedOut(config.requestTimeout) { ctx =>
      log.warn("Request to '{}' timed out, closing the connection", ctx.request.uri)
      close(ctx.connRec)
      ctx.connRec.deliverResponse(Received(Left("Timeout")))
    }
  }

  protected def openRequestCount = openRequests.size
}