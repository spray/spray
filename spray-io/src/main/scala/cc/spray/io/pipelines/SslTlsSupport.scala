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

import akka.event.LoggingAdapter
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import annotation.tailrec
import javax.net.ssl.{SSLEngineResult, SSLEngine}
import SSLEngineResult.Status._
import SSLEngineResult.HandshakeStatus._
import collection.mutable.Queue

object SslTlsSupport {

  def apply(engineCreator: InetSocketAddress => SSLEngine, log: LoggingAdapter): PipelineStage = {
    new DoublePipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
        val engine = engineCreator(context.handle.address)
        var tempBuf = ByteBuffer.allocate(4096)
        val pendingEncrypts = Queue.empty[ByteBuffer]
        val pendingDecrypts = Queue.empty[ByteBuffer]

        def commandPipeline(command: Command) {
          command match {
            case IoPeer.Send(buffers) => encrypt(buffers.toArray)
            case _ => commandPL(command)
          }
        }

        def eventPipeline(event: Event) {
          event match {
            case IoPeer.Received(_, buffer) => decrypt(buffer)
            case _ => eventPL(event)
          }
        }

        @tailrec
        def encrypt(buffers: Array[ByteBuffer]) {
          def sendIfNonEmpty(buffer: ByteBuffer) {
            if (tempBuf.position > 0) {
              val array = new Array[Byte](tempBuf.position)
              System.arraycopy(tempBuf.array, 0, array, 0, tempBuf.position)
              commandPL(IoPeer.Send(ByteBuffer.wrap(array) :: Nil))
            }
            tempBuf.clear()
          }
          val result = engine.wrap(buffers, tempBuf)
          sendIfNonEmpty(tempBuf)
          result.getStatus match {
            case OK => result.getHandshakeStatus match {
              case NOT_HANDSHAKING | FINISHED | NEED_WRAP =>
                if (contentLeft(buffers)) encrypt(buffers)
              case NEED_UNWRAP =>
                queueNonEmptyAsEncrypts(buffers)
              case NEED_TASK =>
                runDelegatedTasks()
                encrypt(buffers)
            }
            case CLOSED =>
              if (contentLeft(buffers)) commandPL(IoPeer.Close(ProtocolError("SSLEngine closed prematurely")))
            case BUFFER_OVERFLOW =>
              tempBuf = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
              encrypt(buffers)
            case BUFFER_UNDERFLOW =>
              throw new IllegalStateException
          }
        }

        @tailrec
        def decrypt(buffer: ByteBuffer): ByteBuffer = {
          def deliverIfNonEmpty(buffer: ByteBuffer) {
            if (tempBuf.position > 0) {
              val array = new Array[Byte](tempBuf.position)
              System.arraycopy(tempBuf.array, 0, array, 0, tempBuf.position)
              eventPL(IoPeer.Received(context.handle, ByteBuffer.wrap(array)))
            }
            tempBuf.clear()
          }
          val result = engine.unwrap(buffer, tempBuf)
          deliverIfNonEmpty(tempBuf)
          result.getStatus match {
            case OK => result.getHandshakeStatus match {
              case NOT_HANDSHAKING | FINISHED | NEED_UNWRAP =>
                if (buffer.remaining > 0) decrypt(buffers)
              case NEED_WRAP =>
                if (buffer.remaining > 0) pendingDecrypts.enqueue(buffer)
              case NEED_TASK =>
                runDelegatedTasks()
                encrypt(buffers)
            }
            case CLOSED =>
              if (contentLeft(buffers)) commandPL(IoPeer.Close(ProtocolError("SSLEngine closed prematurely")))
            case BUFFER_OVERFLOW =>
              tempBuf = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
              encrypt(buffers)
            case BUFFER_UNDERFLOW =>
              throw new IllegalStateException
          }
        }

        @tailrec
        def queueNonEmptyAsEncrypts(buffers: Array[ByteBuffer], ix: Int = 0) {
          if (ix < buffers.length) {
            val b = buffers(ix)
            if (b.remaining > 0) pendingEncrypts.enqueue(b)
            else queueNonEmptyAsEncrypts(buffer, ix + 1)
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
      }
    }
  }
}
