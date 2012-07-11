/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.io
package pipelines

import cc.spray.util._
import akka.event.LoggingAdapter
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.net.ssl.{SSLContext, SSLException, SSLEngineResult, SSLEngine}
import javax.net.ssl.SSLEngineResult.HandshakeStatus._
import SSLEngineResult.Status._
import collection.mutable.Queue
import annotation.tailrec
import scala.Array

object SslTlsSupport {
  def apply(engineProvider: InetSocketAddress => SSLEngine, log: LoggingAdapter): PipelineStage = {
    new DoublePipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
        val engine = engineProvider(context.handle.remoteAddress)
        val pendingSends = Queue.empty[Send]
        var inboundReceptacle: ByteBuffer = _ // holds incoming data that are too small to be decrypted yet

        val commandPipeline: CPL = {
          case x@ IoPeer.Send(buffers, ack) =>
            if (pendingSends.isEmpty) withTempBuf(encrypt(Send(x), _))
            else pendingSends += Send(x)

          case x: IoPeer.Close =>
            log.debug("Closing SSLEngine due to reception of {}", x)
            engine.closeOutbound()
            withTempBuf(closeEngine)
            commandPL(x)

          case cmd => commandPL(cmd)
        }

        val eventPipeline: EPL = {
          case IoPeer.Received(_, buffer) =>
            val buf = if (inboundReceptacle != null) {
              val r = inboundReceptacle; inboundReceptacle = null; r.concat(buffer)
            } else buffer
            withTempBuf(decrypt(buf, _))

          case x: IoPeer.Closed =>
            if (!engine.isOutboundDone) {
              try engine.closeInbound()
              catch { case e: SSLException => } // ignore warning about possible possible truncation attacks
            }
            eventPL(x)

          case ev => eventPL(ev)
        }

        /**
         * Encrypts the given buffers and dispatches the results to the commandPL as IoPeer.Send messages.
         */
        @tailrec
        def encrypt(send: Send, tempBuf: ByteBuffer, fromQueue: Boolean = false) {
          import send._
          log.debug("Encrypting {} buffers with {} bytes", buffers.length, buffers.map(_.remaining).mkString(","))
          tempBuf.clear()
          val sendAckAndPreContentLeft = ack && contentLeft()
          val result = engine.wrap(buffers, tempBuf)
          val postContentLeft = contentLeft()
          tempBuf.flip()
          if (tempBuf.remaining > 0) commandPL {
            IoPeer.Send(tempBuf.copyContent :: Nil, sendAckAndPreContentLeft && !postContentLeft)
          }
          result.getStatus match {
            case OK => result.getHandshakeStatus match {
              case NOT_HANDSHAKING | FINISHED =>
                if (postContentLeft) encrypt(send, tempBuf, fromQueue)
              case NEED_WRAP => encrypt(send, tempBuf, fromQueue)
              case NEED_UNWRAP =>
                if (fromQueue) send +=: pendingSends // output coming from the queue needs to go to the front,
                else pendingSends.enqueue(send)      // "new" output to the back of the queue
              case NEED_TASK =>
                runDelegatedTasks()
                encrypt(send, tempBuf, fromQueue)
            }
            case CLOSED =>
              if (postContentLeft) commandPL(IoPeer.Close(ProtocolError("SSLEngine closed prematurely while sending")))
              false
            case BUFFER_OVERFLOW =>
              throw new IllegalStateException // the SslBufferPool should make sure that buffers are never too small
            case BUFFER_UNDERFLOW =>
              throw new IllegalStateException // BUFFER_UNDERFLOW should never appear as a result of a wrap
          }
        }

        /**
         * Decrypts the given buffer and dispatches the results to the eventPL as an IoPeer.Received message.
         */
        @tailrec
        def decrypt(buffer: ByteBuffer, tempBuf: ByteBuffer) {
          log.debug("Decrypting buffer with {} bytes", buffer.remaining)
          tempBuf.clear()
          val result = engine.unwrap(buffer, tempBuf)
          tempBuf.flip()
          if (tempBuf.remaining > 0) eventPL(IoPeer.Received(context.handle, tempBuf.copyContent))
          result.getStatus match {
            case OK => result.getHandshakeStatus match {
              case NOT_HANDSHAKING | FINISHED =>
                if (buffer.remaining > 0) decrypt(buffer, tempBuf)
                else processPendingSends(tempBuf)
              case NEED_UNWRAP => decrypt(buffer, tempBuf)
              case NEED_WRAP =>
                if (pendingSends.isEmpty) encrypt(Send.Empty, tempBuf)
                else processPendingSends(tempBuf)
                if (buffer.remaining > 0) decrypt(buffer, tempBuf)
              case NEED_TASK =>
                runDelegatedTasks()
                decrypt(buffer, tempBuf)
            }
            case CLOSED =>
              if (!engine.isOutboundDone)
                commandPL(IoPeer.Close(ProtocolError("SSLEngine closed prematurely while receiving")))
            case BUFFER_UNDERFLOW =>
              inboundReceptacle = buffer // save buffer so we can append the next one to it
            case BUFFER_OVERFLOW =>
              throw new IllegalStateException // the SslBufferPool should make sure that buffers are never too small
          }
        }

        def withTempBuf(f: ByteBuffer => Unit) {
          val tempBuf = SslBufferPool.acquire()
          try f(tempBuf)
          catch {
            case e: SSLException =>
              log.error(e, "Closing encrypted connection due to {}", e)
              commandPL(IoPeer.Close(ProtocolError(e.toString)))
          }
          finally SslBufferPool.release(tempBuf)
        }

        @tailrec
        def runDelegatedTasks() {
          val task = engine.getDelegatedTask
          if (task != null) {
            task.run()
            runDelegatedTasks()
          }
        }

        @tailrec
        def processPendingSends(tempBuf: ByteBuffer) {
          if (!pendingSends.isEmpty) {
            val next = pendingSends.dequeue()
            encrypt(next, tempBuf, fromQueue = true)
            // it may be that the send we just passed to `encrypt` was put back into the queue because
            // the SSLEngine demands a `NEED_UNWRAP`, in this case we want to stop looping
            if (!pendingSends.isEmpty && (pendingSends.head ne next))
              processPendingSends(tempBuf)
          }
        }

        @tailrec
        def closeEngine(tempBuf: ByteBuffer) {
          if (!engine.isOutboundDone) {
            encrypt(Send.Empty, tempBuf)
            closeEngine(tempBuf)
          }
        }
      }
    }
  }

  private final case class Send(buffers: Array[ByteBuffer], ack: Boolean) {
    @tailrec
    def contentLeft(ix: Int = 0): Boolean = {
      if (ix < buffers.length) {
        if (buffers(ix).remaining > 0) true
        else contentLeft(ix + 1)
      } else false
    }
  }
  private object Send {
    val Empty = new Send(new Array(0), false)
    def apply(x: IoPeer.Send) = new Send(x.buffers.toArray, x.ack)
  }
}

trait ServerSSLEngineProvider extends (InetSocketAddress => SSLEngine)
object ServerSSLEngineProvider {
  def apply(f: SSLEngine => SSLEngine)(implicit cp: SSLContextProvider): ServerSSLEngineProvider =
    default.andThen(f)

  implicit def default(implicit cp: SSLContextProvider): ServerSSLEngineProvider = {
    new ServerSSLEngineProvider {
      val context = cp.createSSLContext
      def apply(a: InetSocketAddress) =
        make(context.createSSLEngine(a.getHostName, a.getPort))(_.setUseClientMode(false))
    }
  }
  implicit def fromFunc(f: InetSocketAddress => SSLEngine): ServerSSLEngineProvider = {
    new ServerSSLEngineProvider {
      def apply(address: InetSocketAddress) = f(address)
    }
  }
}

trait ClientSSLEngineProvider extends (InetSocketAddress => SSLEngine)
object ClientSSLEngineProvider {
  def apply(f: SSLEngine => SSLEngine)(implicit cp: SSLContextProvider): ClientSSLEngineProvider =
    default.andThen(f)

  implicit def default(implicit cp: SSLContextProvider): ClientSSLEngineProvider = {
    new ClientSSLEngineProvider {
      val context = cp.createSSLContext
      def apply(a: InetSocketAddress) =
        make(context.createSSLEngine(a.getHostName, a.getPort))(_.setUseClientMode(true))
    }
  }
  implicit def fromFunc(f: InetSocketAddress => SSLEngine): ClientSSLEngineProvider = {
    new ClientSSLEngineProvider {
      def apply(address: InetSocketAddress) = f(address)
    }
  }
}

trait SSLContextProvider {
  def createSSLContext: SSLContext
}
object SSLContextProvider {
  implicit def forContext(implicit context: SSLContext = SSLContext.getDefault): SSLContextProvider = {
    new SSLContextProvider {
      def createSSLContext = context
    }
  }
}