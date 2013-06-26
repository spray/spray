/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.httpx.marshalling

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit._
import scala.annotation.tailrec
import akka.spray.UnregisteredActorRef
import akka.util.Timeout
import akka.actor.{ ActorRefFactory, ActorRef }
import spray.http._

/**
 * A MarshallingContext serving as a marshalling receptacle, collecting the output of a Marshaller
 * for subsequent postprocessing.
 */
class CollectingMarshallingContext(implicit actorRefFactory: ActorRefFactory = null) extends MarshallingContext {
  private val _entity = new AtomicReference[Option[HttpEntity]](None)
  private val _error = new AtomicReference[Option[Throwable]](None)
  private val _chunkedMessageEnd = new AtomicReference[Option[ChunkedMessageEnd]](None)
  private val _chunks = new AtomicReference[Seq[MessageChunk]](Vector.empty)
  private val latch = new CountDownLatch(1)

  def entity: Option[HttpEntity] = _entity.get
  def error: Option[Throwable] = _error.get
  def chunks: Seq[MessageChunk] = _chunks.get
  def chunkedMessageEnd: Option[ChunkedMessageEnd] = _chunkedMessageEnd.get

  // we always convert to the first content-type the marshaller can marshal to
  def tryAccept(contentType: ContentType) = Some(contentType)

  def rejectMarshalling(supported: Seq[ContentType]): Unit = {
    handleError(new RuntimeException("Marshaller rejected marshalling, only supports " + supported))
  }

  def marshalTo(entity: HttpEntity): Unit = {
    if (!_entity.compareAndSet(None, Some(entity))) sys.error("`marshalTo` called more than once")
    latch.countDown()
  }

  def handleError(error: Throwable): Unit = {
    _error.compareAndSet(None, Some(error)) // we only save the very first error
    latch.countDown()
  }

  def startChunkedMessage(entity: HttpEntity, ack: Option[Any] = None)(implicit sender: ActorRef) = {
    require(actorRefFactory != null, "Chunked responses can only be collected if an ActorRefFactory is provided")
    if (!_entity.compareAndSet(None, Some(entity)))
      sys.error("`marshalTo` or `startChunkedMessage` was already called")

    val ref = new UnregisteredActorRef(actorRefFactory) {
      def handle(message: Any)(implicit sender: ActorRef) {
        message match {
          case HttpMessagePartWrapper(part, ack) ⇒
            part match {
              case x: MessageChunk ⇒
                @tailrec def updateChunks(current: Seq[MessageChunk]): Unit = {
                  if (!_chunks.compareAndSet(current, _chunks.get :+ x)) updateChunks(_chunks.get)
                }
                updateChunks(_chunks.get)

              case x: ChunkedMessageEnd ⇒
                if (!_chunkedMessageEnd.compareAndSet(None, Some(x)))
                  sys.error("ChunkedMessageEnd received more than once")
                latch.countDown()

              case x ⇒ throw new IllegalStateException("Received unexpected message part: " + x)
            }
            ack.foreach(sender.tell(_, this))
        }
      }
    }
    ack.foreach(sender.tell(_, ref))
    ref
  }

  def awaitResults(implicit timeout: Timeout): Unit = {
    latch.await(timeout.duration.toMillis, MILLISECONDS)
  }
}
