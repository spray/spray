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
import java.nio.channels.spi.SelectorProvider
import java.nio.ByteBuffer
import java.nio.channels.{SocketChannel, SelectionKey, ServerSocketChannel}
import java.io.IOException
import annotation.tailrec
import akka.dispatch._
import utils.LinkedList
import akka.actor.{Scheduler, Actor}
import java.util.concurrent.TimeUnit

// public messages
case object GetServerStats
case class ServerStats(uptime: Long, requestsDispatched: Long, currentConnectionCount: Int)

// private messages
private[can] case object Select
private[can] case object ReapIdleConnections
private[can] case class Respond(key: SelectionKey, rawResponse: RawResponse)

class HttpServer(config: CanConfig) extends Actor with ResponsePreparer {
  private lazy val log = LoggerFactory.getLogger(getClass)
  private lazy val selector = SelectorProvider.provider.openSelector
  private lazy val serverSocketChannel = make(ServerSocketChannel.open) { channel =>
    channel.configureBlocking(false)
    channel.socket.bind(config.endpoint)
    channel.register(selector, SelectionKey.OP_ACCEPT)
  }
  private lazy val serviceActor = Actor.registry.actorsFor(config.serviceActorId) match {
    case Array(head) => head
    case x => throw new RuntimeException("Expected exactly one service actor with id '" +
            config.serviceActorId + "', but found " + x.length)
  }
  private val readBuffer = ByteBuffer.allocateDirect(config.readBufferSize)
  private val connections = new LinkedList[ConnRecord] // a list of all connections registered on the selector
  private var startTime: Long = _
  private var requestsDispatched: Long = _

  self.id = config.serverActorId

  // we use our own custom single-thread dispatcher, because our thread will, for the most time,
  // be blocked at selector selection, therefore we need to wake it up upon message or task arrival,
  // otherwise reaction to new events would be very sluggish
  self.dispatcher = new ImprovedExecutorBasedEventDrivenDispatcher(
    name = "spray-can",
    throughput = -1,
    throughputDeadlineTime = -1,
    mailboxType = UnboundedMailbox(),
    config = ThreadBasedDispatcher.oneThread
  ) {
    override def dispatch(invocation: MessageInvocation) {
      super.dispatch(invocation)
      if (invocation.message != Select) selector.wakeup()
    }

    override def executeTask(invocation: TaskInvocation) {
      super.executeTask(invocation)
      selector.wakeup()
    }
  }

  Scheduler.schedule(self, ReapIdleConnections, config.reapingCycle, config.reapingCycle, TimeUnit.MILLISECONDS)

  override def preStart() {
    log.info("Starting spray-can HTTP server on {}", config.endpoint)
    serverSocketChannel // trigger serverSocketChannel initialization and registration
    self ! Select
    startTime = System.currentTimeMillis()
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error("HttpServer crashed, about to restart...\nmessage: {}\nreason: {}", message.getOrElse("None"), reason)
    cleanUp()
  }

  override def postStop() {
    log.info("Stopped spray-can HTTP server on {}", config.endpoint)
    cleanUp()
  }

  private def cleanUp() {
    selector.close()
    serverSocketChannel.close()
  }

  @inline private def connRecord(key: SelectionKey) = key.attachment.asInstanceOf[ConnRecord]

  protected def receive = {
    case Select => {
      select()
      self ! Select // loop
    }
    case Respond(key, rawResponse) => if (key.isValid) {
      log.debug("Received raw response, scheduling write")
      key.interestOps(SelectionKey.OP_WRITE)
      connRecord(key).load = rawResponse
    }
    case ReapIdleConnections => reapIdleConnections()
    case GetServerStats => {
      log.debug("Received GetServerStats request, responding with stats")
      self.reply {
        ServerStats(System.currentTimeMillis - startTime, requestsDispatched, connections.size)
      }
    }
  }

  private def select() {
    def accept() {
      log.debug("Accepting new connection")
      val socketChannel = serverSocketChannel.accept
      socketChannel.configureBlocking(false)
      val key = socketChannel.register(selector, SelectionKey.OP_READ)
      val connRecord = new ConnRecord(key, load = EmptyRequestParser)
      key.attach(connRecord)
      connections += connRecord
      log.debug("New connection accepted and registered")
    }

    def read(key: SelectionKey) {
      log.debug("Reading from connection")
      val channel = key.channel.asInstanceOf[SocketChannel]
      val connRec = connRecord(key)

      def respond(reqProtocol: HttpProtocol, reqConnectionHeader: Option[String])(response: HttpResponse) {
        // this code is executed from the thread sending the response
        log.debug("Received HttpResponse, enqueuing RawResponse")
        self ! Respond(key, prepare(response, reqProtocol, reqConnectionHeader))
      }

      def dispatch(request: CompleteRequestParser) {
        import request._
        import requestLine._
        log.debug("Dispatching {} request to '{}' to the service actor", method, uri)
        serviceActor ! HttpRequest(method, uri, protocol, headers, body, channel.socket.getInetAddress,
          respond(protocol, connectionHeader))
        requestsDispatched += 1
      }

      def respondWithError(error: ErrorRequestParser) {
        log.debug("Responding with {}", error)
        respond(HttpProtocols.`HTTP/1.1`, None) {
          HttpResponse(error.responseStatus, Nil, (error.message + '\n').getBytes(US_ASCII))
        }
      }

      try {
        readBuffer.clear()
        if (channel.read(readBuffer) > -1) {
          readBuffer.flip()
          log.debug("Read {} bytes", readBuffer.limit())
          connRec.load = connRec.load.asInstanceOf[IntermediateParser].read(readBuffer) match {
            case x: CompleteRequestParser => dispatch(x); EmptyRequestParser
            case x: ErrorRequestParser => respondWithError(x); EmptyRequestParser
            case x => x
          }
          refresh(connRec)
        } else {
          log.debug("Closing connection")
          close(key)
        } // if the client shut down the socket cleanly, we do the same
      }
      catch {
        case e: IOException => {
          // the client forcibly closed the connection
          log.warn("Closing connection due to {}", e.toString)
          close(key)
        }
      }
    }

    def write(key: SelectionKey) {
      log.debug("Writing to connection")
      val channel = key.channel.asInstanceOf[SocketChannel]

      @tailrec
      def writeToChannel(buffers: List[ByteBuffer]): List[ByteBuffer] = {
        if (!buffers.isEmpty) {
          channel.write(buffers.head)
          if (buffers.head.remaining == 0) {
            // if we were able to write the whole buffer
            writeToChannel(buffers.tail) // we continue with the next buffer
          } else buffers // otherwise we cannot drop the head and need to continue with it next time
        } else Nil
      }

      try {
        val connRec = connRecord(key)
        val rawResponse = connRec.load.asInstanceOf[RawResponse]
        connRec.load = writeToChannel(rawResponse.buffers) match {
          case Nil => // we were able to write everything
            if (rawResponse.closeConnection) { // either the protocol or a response header is telling us to close
              close(key)
            } else key.interestOps(SelectionKey.OP_READ) // switch back to reading if we are not closing
            EmptyRequestParser
          case remainingBuffers => // socket buffer full, we couldn't write everything so we stay in writing mode
            RawResponse(remainingBuffers, rawResponse.closeConnection)
        }
        refresh(connRec)
      } catch {
        case e: IOException => { // the client forcibly closed the connection
          log.warn("Closing connection due to {}", e.toString)
          close(key)
        }
      }
    }

    def refresh(rec: ConnRecord) {
      rec.idleSince = System.currentTimeMillis
      connections.moveToEnd(rec)
    }

    selector.select()
    val selectedKeys = selector.selectedKeys.iterator
    while (selectedKeys.hasNext) {
      val key = selectedKeys.next
      selectedKeys.remove()
      if (key.isValid) {
        if (key.isAcceptable) accept()
        else if (key.isReadable) read(key)
        else if (key.isWritable) write(key)
      } else log.warn("Invalid selection key: {}", key)
    }
  }

  private def close(key: SelectionKey) {
    key.cancel()
    try {
      key.channel.close()
    } catch {
      case e: IOException => log.warn("Error while closing socket channel: {}", e.toString)
    }
    connections -= connRecord(key)
  }

  private def reapIdleConnections() {
    val now = System.currentTimeMillis
    connections.traverse { connRec =>
      if (now - connRec.idleSince > config.idleTimeout) {
        log.debug("Closing connection due to idle timout")
        close(connRec.key)
        true // continue the traversal
      } else false // once we reached a connection that hasn't timed out all subsequent ones won't either
    }
  }
}