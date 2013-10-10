/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import javax.net.ssl.{ SSLContext, SSLException, SSLEngineResult, SSLEngine, SSLSession }
import javax.net.ssl.SSLEngineResult.HandshakeStatus._
import scala.collection.immutable.Queue
import scala.annotation.tailrec
import akka.util.ByteString
import akka.io.Tcp
import spray.util._
import SSLEngineResult.Status._

trait SslTlsContext extends PipelineContext {
  /**
   * Returns the SSLEngine to use for encrypting the connection.
   * If undefined the encryption is disabled.
   */
  def sslEngine: Option[SSLEngine]
}

/**
 * A pipeline stage that provides SSL support.
 *
 * One thing to keep in mind is that there's no support for half-closed connections
 * in SSL (but SSL on the other side requires half-closed connections from its transport
 * layer). This means:
 * 1. keepOpenOnPeerClosed is not supported on top of SSL (once you receive PeerClosed the connection is closed, further
 *    CloseCommands are ignored)
 * 2. keepOpenOnPeerClosed should always be enabled on the transport layer beneath SSL so that one can wait for
 *    the other side's SSL level close_notify message without barfing RST to the peer because this socket is already gone
 */
object SslTlsSupport {

  def apply(publishSslSessionInfo: Boolean) = new OptionalPipelineStage[SslTlsContext] {

    def enabled(context: SslTlsContext) = context.sslEngine.isDefined

    def applyIfEnabled(context: SslTlsContext, commandPL: CPL, eventPL: EPL): Pipelines =
      new Pipelines {
        import context._
        val engine = context.sslEngine.get
        var pendingSends = Queue.empty[Send]
        var inboundReceptacle: ByteBuffer = _ // holds incoming data that are too small to be decrypted yet
        var originalCloseCommand: Tcp.CloseCommand = _

        val commandPipeline: CPL = {
          case x: Tcp.Write ⇒
            if (pendingSends.isEmpty) withTempBuf(encrypt(Send(x), _))
            else pendingSends = pendingSends enqueue Send(x)

          case x @ (Tcp.Close | Tcp.ConfirmedClose) ⇒
            originalCloseCommand = x.asInstanceOf[Tcp.CloseCommand]

            log.debug("Closing SSLEngine due to reception of {}", x)
            engine.closeOutbound()
            withTempBuf(closeEngine)

          // don't send close command to network here, it's the job of the SSL engine
          // to shutdown the connection when getting CLOSED in encrypt

          case cmd ⇒ commandPL(cmd)
        }

        val eventPipeline: EPL = {
          case Tcp.Received(data) ⇒
            val buf = if (inboundReceptacle != null) {
              try ByteBuffer.allocate(inboundReceptacle.remaining + data.length).put(inboundReceptacle)
              finally inboundReceptacle = null
            } else ByteBuffer allocate data.length
            data copyToBuffer buf
            buf.flip()
            withTempBuf(decrypt(buf, _))

          case x: Tcp.ConnectionClosed ⇒
            // After we have closed the connection we ignore FIN from the other side.
            // That's to avoid a strange condition where we know that no truncation attack
            // can happen any more (because we actively closed the connection) but the peer
            // isn't behaving properly and didn't send close_notify. Why is this condition strange?
            // Because if we had closed the connection directly after we sent close_notify (which
            // is allowed by the spec) we wouldn't even have noticed.
            if (!engine.isOutboundDone)
              try engine.closeInbound()
              catch { case e: SSLException ⇒ } // ignore warning about possible possible truncation attacks

            if (x.isAborted || (originalCloseCommand eq null)) eventPL(x)
            else if (!engine.isInboundDone) eventPL(originalCloseCommand.event)
          // else close message was sent by decrypt case CLOSED

          case ev ⇒ eventPL(ev)
        }

        def publishSSLSessionEstablished() =
          if (publishSslSessionInfo) eventPL(SSLSessionEstablished(SSLSessionInfo(engine)))

        /**
         * Encrypts the given buffers and dispatches the results to the commandPL as IOPeer.Send messages.
         */
        @tailrec
        def encrypt(send: Send, tempBuf: ByteBuffer, fromQueue: Boolean = false): Unit = {
          import send._
          log.debug("Encrypting {} bytes", buffer.remaining)
          tempBuf.clear()
          val ackDefinedAndPreContentLeft = ack != Tcp.NoAck && buffer.remaining > 0
          val result = engine.wrap(buffer, tempBuf)
          val postContentLeft = buffer.remaining > 0
          tempBuf.flip()
          if (tempBuf.remaining > 0) {
            val writeAck = if (ackDefinedAndPreContentLeft && !postContentLeft) ack else Tcp.NoAck
            commandPL(Tcp.Write(ByteString(tempBuf), writeAck))
          }
          result.getStatus match {
            case OK ⇒ result.getHandshakeStatus match {
              case status @ (NOT_HANDSHAKING | FINISHED) ⇒
                if (status == FINISHED) publishSSLSessionEstablished()
                if (postContentLeft) encrypt(send, tempBuf, fromQueue)
              case NEED_WRAP ⇒ encrypt(send, tempBuf, fromQueue)
              case NEED_UNWRAP ⇒
                pendingSends =
                  if (fromQueue) send +: pendingSends // output coming from the queue needs to go to the front
                  else pendingSends enqueue send // "new" output to the back of the queue
              case NEED_TASK ⇒
                runDelegatedTasks()
                encrypt(send, tempBuf, fromQueue)
            }
            case CLOSED ⇒ // from now on: engine.isOutboundDone == true
              if (postContentLeft) {
                log.warning("SSLEngine closed prematurely while sending")
                commandPL(Tcp.Abort)
              }
              commandPL(Tcp.ConfirmedClose)
            case BUFFER_OVERFLOW ⇒
              throw new IllegalStateException // the SslBufferPool should make sure that buffers are never too small
            case BUFFER_UNDERFLOW ⇒
              throw new IllegalStateException // BUFFER_UNDERFLOW should never appear as a result of a wrap
          }
        }

        /**
         * Decrypts the given buffer and dispatches the results to the eventPL as a Received message.
         */
        @tailrec
        def decrypt(buffer: ByteBuffer, tempBuf: ByteBuffer): Unit = {
          log.debug("Decrypting {} bytes", buffer.remaining)
          tempBuf.clear()
          val result = engine.unwrap(buffer, tempBuf)
          tempBuf.flip()
          if (tempBuf.remaining > 0) eventPL(Tcp.Received(ByteString(tempBuf)))
          result.getStatus match {
            case OK ⇒ result.getHandshakeStatus match {
              case status @ (NOT_HANDSHAKING | FINISHED) ⇒
                if (status == FINISHED) publishSSLSessionEstablished()
                if (buffer.remaining > 0) decrypt(buffer, tempBuf)
                else processPendingSends(tempBuf)
              case NEED_UNWRAP ⇒ decrypt(buffer, tempBuf)
              case NEED_WRAP ⇒
                if (pendingSends.isEmpty) encrypt(Send.Empty, tempBuf)
                else processPendingSends(tempBuf)
                if (buffer.remaining > 0) decrypt(buffer, tempBuf)
              case NEED_TASK ⇒
                runDelegatedTasks()
                decrypt(buffer, tempBuf)
            }
            case CLOSED ⇒ // from now on: engine.isInboundDone == true
              if (originalCloseCommand eq null) {
                closeEngine(tempBuf)
                eventPL(Tcp.PeerClosed)
              } else { // now both sides are closed on the SSL level
                eventPL(originalCloseCommand.event)
                commandPL(Tcp.Close) // close the underlying connection, we don't need it any more
              }
            case BUFFER_UNDERFLOW ⇒
              inboundReceptacle = buffer // save buffer so we can append the next one to it
            case BUFFER_OVERFLOW ⇒
              throw new IllegalStateException // the SslBufferPool should make sure that buffers are never too small
          }
        }

        def withTempBuf(f: ByteBuffer ⇒ Unit): Unit = {
          val tempBuf = SslBufferPool.acquire()
          try f(tempBuf)
          catch {
            case e: SSLException ⇒
              log.error("Closing encrypted connection to {} due to {}", context.remoteAddress, Utils.fullErrorMessageFor(e))
              commandPL(Tcp.Close)
          } finally SslBufferPool release tempBuf
        }

        @tailrec
        def runDelegatedTasks(): Unit = {
          val task = engine.getDelegatedTask
          if (task != null) {
            task.run()
            runDelegatedTasks()
          }
        }

        @tailrec
        def processPendingSends(tempBuf: ByteBuffer): Unit = {
          if (!pendingSends.isEmpty) {
            val next = pendingSends.head
            pendingSends = pendingSends.tail
            encrypt(next, tempBuf, fromQueue = true)
            // it may be that the send we just passed to `encrypt` was put back into the queue because
            // the SSLEngine demands a `NEED_UNWRAP`, in this case we want to stop looping
            if (!pendingSends.isEmpty && pendingSends.head != next)
              processPendingSends(tempBuf)
          }
        }

        @tailrec
        def closeEngine(tempBuf: ByteBuffer): Unit = {
          if (!engine.isOutboundDone) {
            encrypt(Send.Empty, tempBuf)
            closeEngine(tempBuf)
          }
        }
      }
  }

  private final class Send(val buffer: ByteBuffer, val ack: Event)

  private object Send {
    val Empty = new Send(ByteBuffer wrap EmptyByteArray, Tcp.NoAck)
    def apply(write: Tcp.Write) = {
      val buffer = ByteBuffer allocate write.data.length
      write.data copyToBuffer buffer
      buffer.flip()
      new Send(buffer, write.ack)
    }
  }

  /** Event dispatched upon successful SSL handshaking. */
  case class SSLSessionEstablished(info: SSLSessionInfo) extends Event
}

private[io] sealed abstract class SSLEngineProviderCompanion {
  type Self <: (PipelineContext ⇒ Option[SSLEngine])
  protected def clientMode: Boolean

  protected def fromFunc(f: PipelineContext ⇒ Option[SSLEngine]): Self

  def apply(f: SSLEngine ⇒ SSLEngine)(implicit cp: SSLContextProvider): Self =
    fromFunc(default.apply(_) map f)

  implicit def default(implicit cp: SSLContextProvider): Self =
    fromFunc { plc ⇒
      cp(plc) map { sslContext ⇒
        val address = plc.remoteAddress
        val engine = sslContext.createSSLEngine(address.getHostName, address.getPort)
        engine setUseClientMode clientMode
        engine
      }
    }
}

trait ServerSSLEngineProvider extends (PipelineContext ⇒ Option[SSLEngine])
object ServerSSLEngineProvider extends SSLEngineProviderCompanion {
  type Self = ServerSSLEngineProvider
  protected def clientMode = false

  implicit def fromFunc(f: PipelineContext ⇒ Option[SSLEngine]): Self = {
    new ServerSSLEngineProvider {
      def apply(plc: PipelineContext) = f(plc)
    }
  }
}

trait ClientSSLEngineProvider extends (PipelineContext ⇒ Option[SSLEngine])
object ClientSSLEngineProvider extends SSLEngineProviderCompanion {
  type Self = ClientSSLEngineProvider
  protected def clientMode = true

  implicit def fromFunc(f: PipelineContext ⇒ Option[SSLEngine]): Self = {
    new ClientSSLEngineProvider {
      def apply(plc: PipelineContext) = f(plc)
    }
  }
}

trait SSLContextProvider extends (PipelineContext ⇒ Option[SSLContext]) // source-quote-SSLContextProvider
object SSLContextProvider {
  implicit def forContext(implicit context: SSLContext = SSLContext.getDefault): SSLContextProvider =
    fromFunc(_ ⇒ Some(context))

  implicit def fromFunc(f: PipelineContext ⇒ Option[SSLContext]): SSLContextProvider = {
    new SSLContextProvider {
      def apply(plc: PipelineContext) = f(plc)
    }
  }
}
