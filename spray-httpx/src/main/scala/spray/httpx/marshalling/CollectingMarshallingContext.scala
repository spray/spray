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

package spray.httpx.marshalling

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{TimeUnit, CountDownLatch}
import annotation.tailrec
import akka.spray.UnregisteredActorRef
import akka.actor.{ActorRefFactory, ActorRef}
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

  // we always convert to the first CT the marshaller can marshal to
  def tryAccept(contentType: ContentType) = Some(contentType)

  def rejectMarshalling(supported: Seq[ContentType]) {
    handleError(new RuntimeException("Marshaller rejected marshalling, only supports " + supported))
  }

  def marshalTo(entity: HttpEntity) {
    if (!_entity.compareAndSet(None, Some(entity))) sys.error("`marshalTo` called more than once")
    latch.countDown()
  }

  def handleError(error: Throwable) {
    _error.compareAndSet(None, Some(error)) // we only save the very first error
    latch.countDown()
  }

  def startChunkedMessage(entity: HttpEntity, sentAck: Option[Any] = None)(implicit sender: ActorRef) = {
    require(actorRefFactory != null, "Chunked responses can only be collected if an ActorRefFactory is provided")
    if (!_entity.compareAndSet(None, Some(entity)))
      sys.error("`marshalTo` or `startChunkedMessage` was already called")

    val ref = new UnregisteredActorRef(actorRefFactory) {
      def handle(message: Any)(implicit sender: ActorRef) {
        message match {
          case wrapper: HttpMessagePartWrapper =>
            wrapper.messagePart match {
              case x: MessageChunk =>
                @tailrec def updateChunks(current: Seq[MessageChunk]) {
                  if (!_chunks.compareAndSet(current, _chunks.get :+ x)) updateChunks(_chunks.get)
                }
                updateChunks(_chunks.get)

              case x: ChunkedMessageEnd =>
                if (!_chunkedMessageEnd.compareAndSet(None, Some(x)))
                  sys.error("ChunkedMessageEnd received more than once")
                latch.countDown()

              case x => throw new IllegalStateException("Received unexpected message part: " + x)
            }
            wrapper.sentAck.foreach(sender.tell(_, this))
        }
      }
    }
    sentAck.foreach(sender.tell(_, ref))
    ref
  }

  def awaitResults(implicit timeout: akka.util.Timeout) {
    latch.await(timeout.duration.toMillis, TimeUnit.MILLISECONDS)
  }
}
