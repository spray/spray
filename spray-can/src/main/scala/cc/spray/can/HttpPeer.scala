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
import utils.LinkedList
import java.util.concurrent.TimeUnit
import akka.actor._
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.{SocketChannel, SelectionKey}
import annotation.tailrec

case object GetStats
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
private[can] class ConnRecord(val key: SelectionKey, var load: ConnRecordLoad) extends LinkedList.Element[ConnRecord] {
  key.attach(this)
}

private[can] abstract class HttpPeer extends Actor {
  private lazy val log = LoggerFactory.getLogger(getClass)
  protected val readBuffer = ByteBuffer.allocateDirect(config.readBufferSize)
  protected val selector = SelectorProvider.provider.openSelector
  protected val connections = new LinkedList[ConnRecord] // a list of all connections registered on the selector

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

  override def preStart() {
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
    case ReapIdleConnections => reapIdleConnections()
    case GetStats => self.reply(stats)
  }

  protected def select() {
    // The following select() call only really blocks for a longer period of time if the actors mailbox is empty and no
    // other tasks have been scheduled by the dispatcher. Otherwise the dispatcher will either already have called
    // selector.wakeup() (which causes the following call to not block at all) or do so in a short while.
    selector.select()
    val selectedKeys = selector.selectedKeys.iterator
    while (selectedKeys.hasNext) {
      val key = selectedKeys.next
      selectedKeys.remove()
      if (key.isValid) {
        val connRec = key.attachment.asInstanceOf[ConnRecord]
        if (key.isAcceptable) accept()
        else if (key.isReadable) read(connRec)
        else if (key.isWritable) write(connRec)
        else if (key.isConnectable) finishConnection(connRec)
      } else log.warn("Invalid selection key: {}", key)
    }
    self ! Select // loop
  }

  protected def read(connRec: ConnRecord) {
    log.debug("Reading from connection")
    protectIO("Read", connRec) {
      val channel = connRec.key.channel.asInstanceOf[SocketChannel]
      readBuffer.clear()
      if (channel.read(readBuffer) > -1) {
        readBuffer.flip()
        log.debug("Read {} bytes", readBuffer.limit())
        val parser = connRec.load.asInstanceOf[IntermediateParser]
        connRec.load = parser.read(readBuffer) match {
          case x: CompleteMessageParser => readComplete(connRec, x)
          case x: ErrorMessageParser => readParsingError(connRec, x)
          case x => x
        }
        connections.refresh(connRec)
      } else {
        log.debug("Closing connection")
        close(connRec) // if the peer shut down the socket cleanly, we do the same
      }
    }
  }

  protected def write(connRec: ConnRecord) {
    log.debug("Writing to connection")
    val channel = connRec.key.channel.asInstanceOf[SocketChannel]

    @tailrec
    def writeToChannel(buffers: List[ByteBuffer]): List[ByteBuffer] = {
      if (!buffers.isEmpty) {
        channel.write(buffers.head)
        if (buffers.head.remaining == 0) { // if we were able to write the whole buffer
          writeToChannel(buffers.tail)     // we continue with the next buffer
        } else buffers                     // otherwise we cannot drop the head and need to continue with it next time
      } else Nil
    }

    protectIO("Write", connRec) {
      val writeJob = connRec.load.asInstanceOf[WriteJob]
      connRec.load = writeToChannel(writeJob.buffers) match {
        case Nil => // we were able to write everything
          if (writeJob.closeConnection) {
            close(connRec)
          } else {
            connRec.key.interestOps(SelectionKey.OP_READ) // switch back to reading if we are not closing
            connections.refresh(connRec)
          }
          writeComplete(connRec)
        case remainingBuffers => // socket buffer full, we couldn't write everything so we stay in writing mode
          connections.refresh(connRec)
          WriteJob(remainingBuffers, writeJob.closeConnection)
      }
    }
  }

  protected def reapIdleConnections() {
    connections.forAllTimedOut(config.idleTimeout) { connRec =>
      log.debug("Closing connection due to idle timout")
      close(connRec)
    }
  }

  protected def close(connRec: ConnRecord) {
    if (connRec.key.isValid) {
      protectIO("Closing socket") {
        connRec.key.cancel()
        connRec.key.channel.close()
      }
      connections -= connRec
    }
  }

  protected def cleanUp() {
    idleTimeoutCycle.foreach(_.cancel(false))
    requestTimeoutCycle.foreach(_.cancel(false))
    protectIO("Closing selector") {
      selector.close()
    }
  }

  protected def protectIO[A](operation: String, connRec: ConnRecord = null)(body: => A): Either[String, A] = {
    try {
      Right(body)
    } catch {
      case e: IOException => { // maybe the peer forcibly closed the connection?
        val error = e.toString
        if (connRec != null) {
          log.warn("{} error: closing connection due to {}", operation, error)
          close(connRec)
        } else log.warn("{} error: {}", operation, error)
        Left(error)
      }
    }
  }

  protected def stats = {
    log.debug("Received GetStats request, responding with stats")
    Stats(System.currentTimeMillis - startTime, requestsDispatched, requestsTimedOut, openRequestCount, connections.size)
  }

  protected def accept()

  protected def finishConnection(connRec: ConnRecord)

  protected def readComplete(connRec: ConnRecord, parser: CompleteMessageParser): ConnRecordLoad

  protected def readParsingError(connRec: ConnRecord, parser: ErrorMessageParser): ConnRecordLoad

  protected def writeComplete(connRec: ConnRecord): ConnRecordLoad

  protected def handleTimedOutRequests()

  protected def openRequestCount: Int

  protected def config: PeerConfig

}