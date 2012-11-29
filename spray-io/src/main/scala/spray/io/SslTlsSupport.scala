/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.io

import java.nio.ByteBuffer
import javax.net.ssl.{SSLContext, SSLException, SSLEngineResult, SSLEngine}
import javax.net.ssl.SSLEngineResult.HandshakeStatus._
import scala.collection.mutable
import scala.annotation.tailrec
import akka.event.LoggingAdapter
import spray.util._
import SSLEngineResult.Status._


object SslTlsSupport {
  def apply(engineProvider: PipelineContext => SSLEngine, log: LoggingAdapter,
            sslEnabled: PipelineContext => Boolean = _ => true): PipelineStage = {
    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        if (sslEnabled(context)) new SslPipelines(context, commandPL, eventPL)
        else Pipelines(commandPL, eventPL)

      final class SslPipelines(context: PipelineContext, commandPL: CPL, eventPL: EPL) extends Pipelines {
        val engine = engineProvider(context)
        val pendingSends = mutable.Queue.empty[Send]
        var inboundReceptacle: ByteBuffer = _ // holds incoming data that are too small to be decrypted yet

        val commandPipeline: CPL = {
          case x: IOPeer.Send =>
            if (pendingSends.isEmpty) withTempBuf(encrypt(Send(x), _))
            else pendingSends += Send(x)

          case x: IOPeer.Close =>
            log.debug("Closing SSLEngine due to reception of {}", x)
            engine.closeOutbound()
            withTempBuf(closeEngine)
            commandPL(x)

          case cmd => commandPL(cmd)
        }

        val eventPipeline: EPL = {
          case IOPeer.Received(_, buffer) =>
            val buf = if (inboundReceptacle != null) {
              val r = inboundReceptacle; inboundReceptacle = null; r.concat(buffer)
            } else buffer
            withTempBuf(decrypt(buf, _))

          case x: IOPeer.Closed =>
            if (!engine.isOutboundDone) {
              try engine.closeInbound()
              catch { case e: SSLException => } // ignore warning about possible possible truncation attacks
            }
            eventPL(x)

          case ev => eventPL(ev)
        }

        /**
         * Encrypts the given buffers and dispatches the results to the commandPL as IOPeer.Send messages.
         */
        @tailrec
        def encrypt(send: Send, tempBuf: ByteBuffer, fromQueue: Boolean = false) {
          import send._
          log.debug("Encrypting {} buffers with {} bytes", buffers.length, buffers.map(_.remaining).mkString(","))
          tempBuf.clear()
          val ackDefinedAndPreContentLeft = ack.isDefined && contentLeft()
          val result = engine.wrap(buffers, tempBuf)
          val postContentLeft = contentLeft()
          tempBuf.flip()
          if (tempBuf.remaining > 0) commandPL {
            val sendAck = if (ackDefinedAndPreContentLeft && !postContentLeft) ack else None
            IOPeer.Send(tempBuf.copy :: Nil, sendAck)
          }
          result.getStatus match {
            case OK => result.getHandshakeStatus match {
              case NOT_HANDSHAKING | FINISHED =>
                if (postContentLeft) encrypt(send, tempBuf, fromQueue)
              case NEED_WRAP => encrypt(send, tempBuf, fromQueue)
              case NEED_UNWRAP =>
                if (fromQueue) send +=: pendingSends // output coming from the queue needs to go to the front,
                else pendingSends += send            // "new" output to the back of the queue
              case NEED_TASK =>
                runDelegatedTasks()
                encrypt(send, tempBuf, fromQueue)
            }
            case CLOSED =>
              if (postContentLeft) commandPL {
                IOPeer.Close(ConnectionCloseReasons.ProtocolError("SSLEngine closed prematurely while sending"))
              }
            case BUFFER_OVERFLOW =>
              throw new IllegalStateException // the SslBufferPool should make sure that buffers are never too small
            case BUFFER_UNDERFLOW =>
              throw new IllegalStateException // BUFFER_UNDERFLOW should never appear as a result of a wrap
          }
        }

        /**
         * Decrypts the given buffer and dispatches the results to the eventPL as an IOPeer.Received message.
         */
        @tailrec
        def decrypt(buffer: ByteBuffer, tempBuf: ByteBuffer) {
          log.debug("Decrypting buffer with {} bytes", buffer.remaining)
          tempBuf.clear()
          val result = engine.unwrap(buffer, tempBuf)
          tempBuf.flip()
          if (tempBuf.remaining > 0) eventPL(IOBridge.Received(context.connection, tempBuf.copy))
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
              if (!engine.isOutboundDone) commandPL {
                IOPeer.Close(ConnectionCloseReasons.ProtocolError("SSLEngine closed prematurely while receiving"))
              }
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
              log.error(e, "Closing encrypted connection to {} due to {}", context.connection.remoteAddress, e)
              commandPL(IOPeer.Close(ConnectionCloseReasons.ProtocolError(e.toString)))
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

  private final case class Send(buffers: Array[ByteBuffer], ack: Option[Any]) {
    @tailrec
    def contentLeft(ix: Int = 0): Boolean = {
      if (ix < buffers.length) {
        if (buffers(ix).remaining > 0) true
        else contentLeft(ix + 1)
      } else false
    }
  }
  private object Send {
    val Empty = new Send(new Array(0), None)
    def apply(x: IOPeer.Send) = new Send(x.buffers.toArray, x.ack)
  }
}

private[io] sealed abstract class SSLEngineProviderCompanion {
  type Self <: (PipelineContext => SSLEngine)
  protected def clientMode: Boolean

  protected def fromFunc(f: PipelineContext => SSLEngine): Self

  def apply(f: SSLEngine => SSLEngine)(implicit cp: SSLContextProvider): Self =
    fromFunc(default.andThen(f))

  implicit def default(implicit cp: SSLContextProvider): Self =
    fromFunc { plc =>
      val sslContext = cp(plc)
      val remoteAddress = plc.connection.remoteAddress
      val engine = sslContext.createSSLEngine(remoteAddress.getHostName, remoteAddress.getPort)
      engine.setUseClientMode(clientMode)
      engine
    }
}

trait ServerSSLEngineProvider extends (PipelineContext => SSLEngine)
object ServerSSLEngineProvider extends SSLEngineProviderCompanion {
  type Self = ServerSSLEngineProvider
  protected def clientMode = false

  implicit def fromFunc(f: PipelineContext => SSLEngine): Self = {
    new ServerSSLEngineProvider {
      def apply(plc: PipelineContext) = f(plc)
    }
  }
}

trait ClientSSLEngineProvider extends (PipelineContext => SSLEngine)
object ClientSSLEngineProvider extends SSLEngineProviderCompanion {
  type Self = ClientSSLEngineProvider
  protected def clientMode = true

  implicit def fromFunc(f: PipelineContext => SSLEngine): Self = {
    new ClientSSLEngineProvider {
      def apply(plc: PipelineContext) = f(plc)
    }
  }
}

trait SSLContextProvider extends (PipelineContext => SSLContext)
object SSLContextProvider {
  implicit def forContext(implicit context: SSLContext = SSLContext.getDefault): SSLContextProvider =
    fromFunc(_ => context)

  implicit def fromFunc(f: PipelineContext => SSLContext): SSLContextProvider = {
    new SSLContextProvider {
      def apply(plc: PipelineContext) = f(plc)
    }
  }
}