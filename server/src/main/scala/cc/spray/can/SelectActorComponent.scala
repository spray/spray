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

import java.nio.channels.spi.SelectorProvider
import akka.dispatch.Dispatchers
import java.nio.ByteBuffer
import org.slf4j.LoggerFactory
import akka.actor.Actor
import java.nio.channels.{SocketChannel, SelectionKey, ServerSocketChannel}
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import annotation.tailrec
import utils.PostStart

private[can] object Constants {
  val US_ASCII = Charset.forName("US-ASCII")
  val HttpVersionPlusSP = "HTTP/1.1 ".getBytes(US_ASCII)
  val ColonSP = ": ".getBytes(US_ASCII)
  val CRLF = "\r\n".getBytes(US_ASCII)
  val SingleSP = " ".getBytes(US_ASCII)
  val StatusLine200 = "HTTP/1.1 200 OK\r\n".getBytes(US_ASCII)
}

trait SelectActorComponent {
  this: ResponsePreparer =>

  def config: CanConfig

  private[can] val started = new CountDownLatch(1)
  private[can] val stopped = new CountDownLatch(1)
  private[can] val selector = SelectorProvider.provider.openSelector

  class SelectActor extends Actor with PostStart {
    private val log = LoggerFactory.getLogger(getClass)
    private val serverSocketChannel = make(ServerSocketChannel.open) { channel =>
      channel.configureBlocking(false)
      channel.socket.bind(config.endpoint)
      channel.register(selector, SelectionKey.OP_ACCEPT)
    }
    private lazy val dispatchActor = Actor.registry.actorsFor(config.dispatchActorId) match {
      case Array(head) => head
      case x => throw new RuntimeException("Expected exactly one dispatch actor with id '" +
              config.dispatchActorId + "', but found " + x.length)
    }
    private val readBuffer = ByteBuffer.allocateDirect(config.readBufferSize)

    // this actor runs in its own private thread
    self.dispatcher = Dispatchers.newThreadBasedDispatcher(self)

    def postStart() {
      log.debug("SelectActor started")
      started.countDown()
      self ! Select // kick off the Select loop
    }

    override def preRestart(reason: Throwable, message: Option[Any]) {
      log.error("SelectActor crashed, about to restart...\nmessage: {}\nreason: {}", message.getOrElse("None"), reason)
      cleanUp()
    }

    override def postStop() {
      log.debug("SelectActor stopped")
      cleanUp()
      stopped.countDown()
    }

    private def cleanUp() {
      selector.close()
      serverSocketChannel.close()
    }

    protected def receive = {
      case Select => select()
      case Respond(key, rawResponse) => {
        log.debug("Received raw response, scheduling write")
        key.interestOps(SelectionKey.OP_WRITE)
        key.attach(rawResponse)
      }
    }

    private def select() {
      selector.select()
      val selectedKeys = selector.selectedKeys.iterator
      while (selectedKeys.hasNext) {
        val key = selectedKeys.next
        selectedKeys.remove()
        if (key.isValid) {
          if (key.isAcceptable) accept()
          else if (key.isReadable) read(key)
          else if (key.isWritable) write(key)
        }
      }
      self ! Select
    }

    private def accept() {
      log.debug("Accepting new connection")
      val socketChannel = serverSocketChannel.accept
      socketChannel.configureBlocking(false)
      val key = socketChannel.register(selector, SelectionKey.OP_READ)
      key.attach(EmptyRequestParser)
      log.debug("New connection accepted and registered")
    }

    private def read(key: SelectionKey) {
      log.debug("Reading from connection")
      val channel = key.channel.asInstanceOf[SocketChannel]
      val requestParser = key.attachment.asInstanceOf[IntermediateParser]

      def respond(response: HttpResponse) {
        // this code is executed from the thread sending the response
        self ! Respond(key, prepare(response))
        selector.wakeup() // the SelectActor is probably blocked at the "selector.select()" call, so wake it up
      }

      def dispatch(request: CompleteRequestParser) {
        log.debug("Dispatching response")
        import request._
        dispatchActor ! HttpRequest(method, uri, headers.reverse, body, channel.socket.getInetAddress, respond)
      }

      def respondWithError(error: ErrorRequestParser) {
        log.debug("Responding with error response")
        respond(HttpResponse(error.responseStatus, Nil, (error.message + ":\n").getBytes(Constants.US_ASCII)))
      }

      def close() {
        key.cancel()
        channel.close()
      }

      try {
        readBuffer.clear()
        if (channel.read(readBuffer) > -1) {
          readBuffer.flip()

          log.debug("Read {} bytes", readBuffer.limit())

          key.attach {
            requestParser.read(readBuffer) match {
              case x: CompleteRequestParser => dispatch(x); EmptyRequestParser
              case x: ErrorRequestParser => respondWithError(x); EmptyRequestParser
              case x => x
            }
          }
        } else {
          close()
        } // if the client shut down the socket cleanly, we do the same
      }
      catch {
        case e: IOException => {
          // the client forcibly closed the connection
          log.debug("Closing connection due to {}", e)
          close()
        }
      }
    }

    private def write(key: SelectionKey) {
      log.debug("Writing to connection")
      val channel = key.channel.asInstanceOf[SocketChannel]
      val rawResponse = key.attachment.asInstanceOf[List[ByteBuffer]]

      @tailrec
      def writeToChannel(buffers: List[ByteBuffer]): List[ByteBuffer] = {
        if (!buffers.isEmpty) {
          channel.write(buffers.head)
          if (buffers.head.remaining == 0) {
            // if we were able to write the whole buffer
            writeToChannel(buffers.tail) // we continue with the next buffer
          } else {
            buffers
          } // otherwise we cannot drop the head and need to continue with it next time
        } else {
          Nil
        }
      }

      writeToChannel(rawResponse) match {
        case Nil => // we were able to write everything, so we can switch back to reading
          key.interestOps(SelectionKey.OP_READ)
          key.attach(EmptyRequestParser)
        case remainingBuffers => // socket buffer full, we couldn't write everything so we stay in writing mode
          key.attach(remainingBuffers)
      }
    }
  }

}

private[can] case object Select
private[can] case class Respond(key: SelectionKey, rawResponse: List[ByteBuffer])