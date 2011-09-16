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
import akka.dispatch.Future
import java.nio.ByteBuffer
import collection.mutable.Queue
import akka.actor.{Actor, UntypedChannel}
import java.lang.IllegalStateException

sealed trait HttpConnection {
  def send(request: HttpRequest): Future[HttpResponse]
  def close()
}

// public incoming messages
case class Connect(host: String, port: Int = 80)

object HttpClient extends HighLevelHttpClient {
  private case class Send(conn: ClientConnection, request: HttpRequest, buffers: List[ByteBuffer])
  private case class Close(conn: ClientConnection)
  private case class RequestRecord(request: HttpRequest, conn: ClientConnection)
          extends LinkedList.Element[RequestRecord]

  private[can] class ClientConnection(key: SelectionKey, val host: String, val port: Int,
                                 val connectionResponseChannel: Option[UntypedChannel] = None)
          extends Connection[ClientConnection](key) {
    val responseQueue = Queue.empty[Either[String, HttpResponse] => Unit]
    key.attach(this)
    messageParser = new EmptyResponseParser
  }
}

final class HttpClient(val config: ClientConfig = ClientConfig.fromAkkaConf) extends HttpPeer {
  import HttpClient._

  private lazy val log = LoggerFactory.getLogger(getClass)
  private val openRequests = new LinkedList[RequestRecord]

  private[can] type Conn = ClientConnection

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
    case Connect(host, port) => initiateConnection(host, port)
    case send: Send => {
      if (send.conn.writeBuffers.isEmpty) {
        prepareWriting(send)
      } else self.forward(send) // we still have a previous write ongoing, so try again later (but keep the response channel)
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
        new HttpConnectionImpl(conn)
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
          conn.disableWriting()
          connections += conn
          log.debug("Connected to {}:{}", conn.host, conn.port)
          new HttpConnectionImpl(conn)
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
      log.debug("Received raw request, scheduling write")
      val requestRecord = RequestRecord(request, conn)
      val responseChannel = self.channel
      conn.responseQueue.enqueue { either =>
        either match {
          case Right(response) => responseChannel ! response
          case Left(error) => responseChannel.sendException(new HttpClientException(error))
        }
        openRequests -= requestRecord
      }
      conn.writeBuffers = buffers
      conn.enableWriting()
      openRequests += requestRecord
      requestsDispatched += 1
    } else {
      self.channel.sendException(new HttpClientException("Cannot send request due to closed connection"))
    }
  }

  protected def handleMessageParsingComplete(conn: Conn, parser: CompleteMessageParser) {
    import parser._
    val statusLine = messageLine.asInstanceOf[StatusLine]
    import statusLine._
    val response = HttpResponse(status, headers, body, protocol)
    conn.responseQueue.dequeue().apply(Right(response))
    conn.messageParser = new EmptyResponseParser // reset for parsing the next response
  }

  protected def handleMessageParsingError(conn: Conn, parser: ErrorMessageParser) {
    conn.responseQueue.foreach(_(Left(parser.message)))
    // In case of a response parsing error we probably stopped reading the response somewhere in between, where we
    // cannot simply resume. Resetting to a known state is not easy either, so we need to close the connection to do so.
    close(conn)
  }

  protected def finishWrite(conn: ClientConnection) {
    if (conn.writeBuffers.isEmpty) {
      conn.disableWriting()
    }
  }

  override protected def cleanClose(conn: Conn) {
    super.cleanClose(conn)
    conn.responseQueue.foreach(_(Left("Server closed connection")))
  }

  protected def handleTimedOutRequests() {
    openRequests.forAllTimedOut(config.requestTimeout) { requestRecord =>
      log.warn("Request to '{}' timed out, closing the connection", requestRecord.request.uri)
      requestRecord.conn.responseQueue.foreach(_(Left("Request timed out")))
      close(requestRecord.conn)
    }
  }

  override protected def reapConnection(conn: Conn) {
    conn.responseQueue.foreach(_(Left("Connection closed due to idle timeout")))
    super.reapConnection(conn)
  }

  protected def openRequestCount = openRequests.size

  private class HttpConnectionImpl(conn: ClientConnection) extends HttpConnection with RequestPreparer {
    protected def userAgentHeader = config.userAgentHeader

    def send(request: HttpRequest) = {
      HttpRequest.verify(request)
      log.debug("Enqueueing valid HttpRequest as raw request")
      implicit val timeout = Actor.Timeout(Long.MaxValue) // "disable" the akka future, since we rely on our own
      (self ? Send(conn, request, prepare(request, conn.host, conn.port))).mapTo[HttpResponse]
    }

    def close() {
      self ! Close(conn)
    }
  }
}

class HttpClientException(message: String) extends RuntimeException(message)