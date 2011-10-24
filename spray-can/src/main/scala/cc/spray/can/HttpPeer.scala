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

import java.nio.ByteBuffer
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.TimeUnit
import akka.actor._
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.{SocketChannel, SelectionKey}
import annotation.tailrec

/////////////////////////////////////////////
// HttpPeer messages
////////////////////////////////////////////

/**
 * Both, the [[cc.spray.can.HttpServer]] as well as the [[cc.spray.can.HttpClient]] respond to `GetStats` messages
 * by replying with an instance of [[cc.spray.can.Stats]].
 */
case object GetStats

/**
 * Both, the [[cc.spray.can.HttpServer]] as well as the [[cc.spray.can.HttpClient]] respond to
 * `GetStats` messages by replying with an instance of `Stats`.
 */
case class Stats(
  uptime: Long,
  requestsDispatched: Long,
  requestsTimedOut: Long,
  requestsOpen: Int,
  connectionsOpen: Int
)

private[can] case object Select
private[can] case object ReapIdleConnections
private[can] case object HandleTimedOutRequests


/////////////////////////////////////////////
// HttpPeer
////////////////////////////////////////////

// as soon as a connection is properly established a Connection instance
// is created and permanently attached to the connections SelectionKey
private[can] abstract class Connection[T >: Null <: LinkedList.Element[T]](val key: SelectionKey)
        extends LinkedList.Element[T] {
  var writeBuffers: List[ByteBuffer] = Nil
  var messageParser: MessageParser = _

  import SelectionKey._
  var interestOps = OP_READ
  def enableWriting() { key.interestOps { interestOps |= OP_WRITE; interestOps } }
  def disableWriting() { key.interestOps { interestOps &= ~OP_WRITE; interestOps } }
  def disableReading() { key.interestOps { interestOps &= ~OP_READ; interestOps } }
}

private[can] abstract class HttpPeer(threadName: String) extends Actor {
  private lazy val log = LoggerFactory.getLogger(getClass)

  private[can] type Conn >: Null <: Connection[Conn]
  protected def config: PeerConfig

  protected val readBuffer = ByteBuffer.allocateDirect(config.readBufferSize)
  protected val selector = SelectorProvider.provider.openSelector
  protected val connections = new LinkedList[Conn] // a list of all connections registered on the selector

  // statistics
  protected var startTime: Long = _
  protected var requestsDispatched: Long = _
  protected var requestsTimedOut: Long = _

  protected val idleTimeoutCycle = if (config.idleTimeout == 0) None else Some {
    Scheduler.schedule(() => self ! ReapIdleConnections, config.reapingCycle, config.reapingCycle, TimeUnit.MILLISECONDS)
  }
  protected val requestTimeoutCycle = if (config.requestTimeout == 0) None else Some {
    Scheduler.schedule(() => self ! HandleTimedOutRequests, config.timeoutCycle, config.timeoutCycle, TimeUnit.MILLISECONDS)
  }

  // we use our own custom single-thread dispatcher, because our thread will, for the most time,
  // be blocked at selector selection, therefore we need to wake it up upon message or task arrival
  if (self.isBeingRestarted) {
    self.dispatcher.asInstanceOf[SelectorWakingDispatcher].selector = selector
  } else {
    self.dispatcher = new SelectorWakingDispatcher(threadName, selector)
  }

  override def preStart() {
    // CAUTION: as of Akka 2.0 this method will not be called during a restart
    startTime = System.currentTimeMillis()
    self ! Select // start the selection loop
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error(getClass.getSimpleName +" crashed, about to restart...\nmessage: {}\nreason: {}",
      message.getOrElse("None"), reason)
    cleanUp()
  }

  override def postStop() {
    cleanUp()
  }

  protected def receive = {
    case Select => select()
    case HandleTimedOutRequests => handleTimedOutRequests()
    case ReapIdleConnections => connections.forAllTimedOut(config.idleTimeout)(reapConnection)
    case GetStats => self.reply(stats)
  }

  private def select() {
    // The following select() call only really blocks for a longer period of time if the actors mailbox is empty and no
    // other tasks have been scheduled by the dispatcher. Otherwise the dispatcher will either already have called
    // selector.wakeup() (which causes the following call to not block at all) or do so in a short while.
    selector.select()
    val selectedKeys = selector.selectedKeys.iterator
    while (selectedKeys.hasNext) {
      val key = selectedKeys.next
      selectedKeys.remove()
      if (key.isValid) {
        if (key.isWritable) write(key) // favor writes if writeable as well as readable
        else if (key.isReadable) read(key)
        else handleConnectionEvent(key)
      } else log.warn("Invalid selection key: {}", key)
    }
    self ! Select // loop
  }

  private def read(key: SelectionKey) {
    val conn = key.attachment.asInstanceOf[Conn]

    @tailrec def parseReadBuffer() {
      conn.messageParser match {
        case x: IntermediateParser =>
          val recurse = x.read(readBuffer) match {
            case x: IntermediateParser => conn.messageParser = x; false
            case x: CompleteMessageParser => handleCompleteMessage(conn, x); true
            case x: ChunkedStartParser => handleChunkedStart(conn, x); true
            case x: ChunkedChunkParser => handleChunkedChunk(conn, x); true
            case x: ChunkedEndParser => handleChunkedEnd(conn, x); true
            case x: ErrorParser => handleParseError(conn, x); false
          }
          if (recurse && readBuffer.remaining > 0) parseReadBuffer()
        case x: ErrorParser => handleParseError(conn, x)
      }
    }

    protectIO("Read", conn) {
      val channel = key.channel.asInstanceOf[SocketChannel]
      readBuffer.clear()
      if (channel.read(readBuffer) > -1) {
        readBuffer.flip()
        log.debug("Read {} bytes", readBuffer.limit())
        parseReadBuffer()
        if (conn.memberOf == connections) // otherwise the conn has been closed during parser handling
          connections.refresh(conn)
      } else cleanClose(conn) // if the peer shut down the socket cleanly, we do the same
    }
  }

  protected def cleanClose(conn: Conn) {
    log.debug("Peer orderly closed connection")
    close(conn)
  }

  private def write(key: SelectionKey) {
    val conn = key.attachment.asInstanceOf[Conn]
    log.debug("Writing to connection")
    val channel = key.channel.asInstanceOf[SocketChannel]

    @tailrec
    def writeToChannel(buffers: List[ByteBuffer]): List[ByteBuffer] = {
      if (!buffers.isEmpty) {
        channel.write(buffers.head)
        if (buffers.head.remaining == 0) { // if we were able to write the whole buffer
          writeToChannel(buffers.tail)     // we continue with the next buffer
        } else buffers                     // otherwise we cannot drop the head and need to continue with it next time
      } else Nil
    }

    protectIO("Write", conn) {
      conn.writeBuffers = writeToChannel(conn.writeBuffers)
      connections.refresh(conn)
      finishWrite(conn)
    }
  }

  protected def reapConnection(conn: Conn) {
    log.debug("Closing connection due to idle timout")
    close(conn)
  }

  protected def close(conn: Conn) {
    if (conn.key.isValid) {
      protectIO("Closing socket") {
        conn.key.cancel()
        conn.key.channel.close()
      }
      connections -= conn
    }
  }

  protected def cleanUp() {
    log.debug("Cleaning up scheduled tasks and NIO selector")
    idleTimeoutCycle.foreach(_.cancel(false))
    requestTimeoutCycle.foreach(_.cancel(false))
    protectIO("Closing selector") {
      selector.close()
    }
  }

  protected final def protectIO[A](operation: String, conn: Conn = null)(body: => A): Either[String, A] = {
    try {
      Right(body)
    } catch {
      case e: IOException => { // probably the peer forcibly closed the connection
        val error = e.toString
        if (conn != null) {
          log.warn("{} error: closing connection due to {}", operation, error)
          close(conn)
        } else log.warn("{} error: {}", operation, error)
        Left(error)
      }
    }
  }

  protected def stats = {
    log.debug("Received GetStats request, responding with stats")
    Stats(System.currentTimeMillis - startTime, requestsDispatched, requestsTimedOut, openRequestCount, connections.size)
  }

  protected def handleConnectionEvent(key: SelectionKey)

  protected def handleCompleteMessage(conn: Conn, parser: CompleteMessageParser)

  protected def handleChunkedStart(conn: Conn, parser: ChunkedStartParser)

  protected def handleChunkedChunk(conn: Conn, parser: ChunkedChunkParser)

  protected def handleChunkedEnd(conn: Conn, parser: ChunkedEndParser)

  protected def handleParseError(conn: Conn, parser: ErrorParser)

  protected def finishWrite(conn: Conn)

  protected def handleTimedOutRequests()

  protected def openRequestCount: Int

}