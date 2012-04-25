/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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
import javax.net.ssl.{SSLException, SSLEngineResult, SSLEngine}
import SSLEngineResult.Status._
import SSLEngineResult.HandshakeStatus._
import collection.mutable.Queue
import annotation.tailrec

object SslTlsSupport {

  def apply(engineCreator: InetSocketAddress => SSLEngine, log: LoggingAdapter): PipelineStage = {
    new DoublePipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
        val engine = engineCreator(context.handle.address)
        val pendingEncrypts = Queue.empty[ByteBuffer]
        val pendingDecrypts = Queue.empty[ByteBuffer]
        var receptacle: ByteBuffer = _ // holds incoming data that are too small to be decrypted

        val commandPipeline: CPL = {
          case IoPeer.Send(buffers) =>
            if (pendingEncrypts.isEmpty) withTempBuf(encrypt(buffers.toArray, fromQueue = false))
            else queueNonEmptyForSending(buffers.toArray)

          case x: IoPeer.Close =>
            engine.closeOutbound()
            withTempBuf(closeEngine)
            commandPL(x)

          case cmd => commandPL(cmd)
        }

        val eventPipeline: EPL = {
          case IoPeer.Received(_, buffer) =>
            if (receptacle != null) {
              val r = receptacle; receptacle = null
              withTempBuf(decrypt(r.concat(buffer), fromQueue = false))
            } else if (pendingDecrypts.isEmpty)
              withTempBuf(decrypt(buffer, fromQueue = false))
            else queueNonEmptyForReceiving(buffer)

          case x: IoPeer.Closed =>
            if (!engine.isOutboundDone) withTempBuf(
              tempBuf => {
                engine.closeInbound()
                closeEngine(tempBuf)
              },
              ignoreSSLExceptions = true
            )
            eventPL(x)

          case ev => eventPL(ev)
        }

        /**
         * Encrypts the given buffers and dispatches the results to the commandPL as IoPeer.Send messages.
         * Returns `true` if the SSLEngine is ready to send more data, false otherwise.
         */
        @tailrec
        def encrypt(buffers: Array[ByteBuffer], fromQueue: Boolean)(tempBuf: ByteBuffer): Boolean = {
          log.debug("Encrypting {} buffers with {} bytes", buffers.size, buffers.map(_.remaining).mkString(","))
          tempBuf.clear()
          val result = engine.wrap(buffers, tempBuf)
          tempBuf.flip()
          if (tempBuf.remaining > 0) commandPL(IoPeer.Send(tempBuf.copyContent :: Nil))
          result.getStatus match {
            case OK => result.getHandshakeStatus match {
              case NOT_HANDSHAKING | FINISHED | NEED_WRAP =>
                if (!contentLeft(buffers)) {
                  processPendingDecrypts(tempBuf)
                  true
                } else encrypt(buffers, fromQueue)(tempBuf)
              case NEED_UNWRAP if !pendingDecrypts.isEmpty =>
                processPendingDecrypts(tempBuf)
                encrypt(buffers, fromQueue)(tempBuf)
              case NEED_UNWRAP =>
                // output that has been queued before needs to go to the front of the queue, "new" output to the back
                if (fromQueue) frontQueueNonEmptyForSending(buffers)
                else queueNonEmptyForSending(buffers)
                false
              case NEED_TASK =>
                runDelegatedTasks()
                encrypt(buffers, fromQueue)(tempBuf)
            }
            case CLOSED =>
              if (contentLeft(buffers))
                commandPL(IoPeer.Close(ProtocolError("SSLEngine closed prematurely while sending")))
              false
            case BUFFER_OVERFLOW =>
              throw new IllegalStateException // the SslBufferPool should make sure that buffers are never too small
            case BUFFER_UNDERFLOW =>
              throw new IllegalStateException // BUFFER_UNDERFLOW should never appear as a result of a wrap
          }
        }

        /**
         * Decrypts the given buffer and dispatches the results to the eventPL as an IoPeer.Received message.
         * Returns `true` if the SSLEngine is ready to received more data, false otherwise.
         */
        @tailrec
        def decrypt(buffer: ByteBuffer, fromQueue: Boolean)(tempBuf: ByteBuffer): Boolean = {
          log.debug("Decrypting buffer with {} bytes", buffer.remaining)
          tempBuf.clear()
          val result = engine.unwrap(buffer, tempBuf)
          tempBuf.flip()
          if (tempBuf.remaining > 0) eventPL(IoPeer.Received(context.handle, tempBuf.copyContent))
          result.getStatus match {
            case OK => result.getHandshakeStatus match {
              case NOT_HANDSHAKING | FINISHED | NEED_UNWRAP =>
                if (buffer.remaining == 0) {
                  processPendingEncrypts(tempBuf)
                  true
                } else decrypt(buffer, fromQueue)(tempBuf)
              case NEED_WRAP if !pendingEncrypts.isEmpty =>
                processPendingEncrypts(tempBuf)
                buffer.remaining > 0 && decrypt(buffer, fromQueue)(tempBuf)
              case NEED_WRAP =>
                // input that has been queued before needs to go to the front of the queue, "new" input to the back
                if (fromQueue) frontQueueNonEmptyForReceiving(buffer)
                else queueNonEmptyForReceiving(buffer)
                false
              case NEED_TASK =>
                runDelegatedTasks()
                buffer.remaining > 0 && decrypt(buffer, fromQueue)(tempBuf)
            }
            case CLOSED =>
              if (!engine.isOutboundDone)
                commandPL(IoPeer.Close(ProtocolError("SSLEngine closed prematurely while receiving")))
              false
            case BUFFER_UNDERFLOW =>
              if (pendingDecrypts.isEmpty) {
                receptacle = buffer
                false
              } else decrypt(buffer.concat(drainToArray(pendingDecrypts)), false)(tempBuf)
            case BUFFER_OVERFLOW =>
              throw new IllegalStateException // the SslBufferPool should make sure that buffers are never too small
          }
        }

        def withTempBuf(f: ByteBuffer => Boolean, ignoreSSLExceptions: Boolean = false): Boolean = {
          val tempBuf = SslBufferPool.acquire()
          try f(tempBuf)
          catch {
            case e: SSLException =>
              if (!ignoreSSLExceptions) commandPL(IoPeer.Close(ProtocolError(e.toString)))
              false
          }
          finally SslBufferPool.release(tempBuf)
        }

        @tailrec
        def queueNonEmptyForSending(buffers: Array[ByteBuffer], ix: Int = 0) {
          if (ix < buffers.length) {
            val b = buffers(ix)
            if (b.remaining > 0) pendingEncrypts.enqueue(b)
            queueNonEmptyForSending(buffers, ix + 1)
          }
        }

        @tailrec
        def frontQueueNonEmptyForSending(buffers: Array[ByteBuffer], ix: Int = 1) {
          if (ix <= buffers.length) {
            val b = buffers(buffers.length - ix)
            if (b.remaining > 0) b +=: pendingEncrypts
            frontQueueNonEmptyForSending(buffers, ix + 1)
          }
        }

        def queueNonEmptyForReceiving(buffer: ByteBuffer) {
          if (buffer.remaining > 0) {
            pendingDecrypts.enqueue(buffer)
          }
        }

        def frontQueueNonEmptyForReceiving(buffer: ByteBuffer) {
          if (buffer.remaining > 0) {
            buffer +=: pendingDecrypts
          }
        }

        @tailrec
        def contentLeft(buffers: Array[ByteBuffer], ix: Int = 0): Boolean = {
          if (ix < buffers.length) {
            if (buffers(ix).remaining > 0) true
            else contentLeft(buffers, ix + 1)
          } else false
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
        def processPendingDecrypts(tempBuf: ByteBuffer) {
          if (!pendingDecrypts.isEmpty && decrypt(pendingDecrypts.dequeue(), fromQueue = true)(tempBuf))
            processPendingDecrypts(tempBuf)
        }

        @tailrec
        def processPendingEncrypts(tempBuf: ByteBuffer) {
          if (!pendingEncrypts.isEmpty && encrypt(drainToArray(pendingEncrypts), fromQueue = true)(tempBuf))
            processPendingEncrypts(tempBuf)
        }

        def drainToArray(queue: Queue[ByteBuffer]): Array[ByteBuffer] = {
          val array = queue.toArray
          queue.clear()
          array
        }

        @tailrec
        def closeEngine(tempBuf: ByteBuffer): Boolean = {
          if (!engine.isOutboundDone) {
            encrypt(new Array(0), fromQueue = false)(tempBuf)
            closeEngine(tempBuf)
          } else false
        }
      }
    }
  }
}
