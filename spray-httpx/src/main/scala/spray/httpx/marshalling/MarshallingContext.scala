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

import spray.http.{ HttpBody, ContentType, HttpEntity }
import akka.actor.ActorRef

trait MarshallingContext { self ⇒

  /**
   * Determines whether the given ContentType is acceptable.
   * If the given ContentType does not define a charset an accepted charset is selected, i.e. the method guarantees
   * that, if a ContentType instance is returned within the option, it will contain a defined charset.
   */
  def tryAccept(contentType: ContentType): Option[ContentType]

  /**
   * Signals that the Marshaller rejects the marshalling request because
   * none of its target ContentTypes is accepted.
   */
  def rejectMarshalling(supported: Seq[ContentType])

  /**
   * Receives the end product entity of a Marshalling.
   */
  def marshalTo(entity: HttpEntity)

  /**
   * Handles the given error.
   * Calling this method rather than throwing the exception directly allows the error to transcend thread boundaries
   * and contexts, e.g. when channeling an error from a future execution back to the original scope.
   */
  def handleError(error: Throwable)

  /**
   * Uses the given entity to start a chunked response stream.
   * The method returns an ActorRef that should be used as the channel for subsequent [[spray.http.MessageChunk]]
   * instances and the finalizing [[spray.http.ChunkedMessageEnd]].
   * If a ack is defined it will be sent back to the sender after the initial message part has been successfully
   * passed to the network.
   */
  def startChunkedMessage(entity: HttpEntity, ack: Option[Any] = None)(implicit sender: ActorRef): ActorRef

  /**
   * Creates a new MarshallingContext based on this one, that overrides the ContentType of the produced entity
   * with the given one.
   */
  def withContentTypeOverriding(contentType: ContentType): MarshallingContext =
    new DelegatingMarshallingContext(self) {
      override def tryAccept(ct: ContentType) =
        Some(if (contentType.isCharsetDefined) ct.withCharset(contentType.charset) else ct)
      override def marshalTo(entity: HttpEntity): Unit = { self.marshalTo(overrideContentType(entity)) }
      override def startChunkedMessage(entity: HttpEntity, ack: Option[Any])(implicit sender: ActorRef) =
        self.startChunkedMessage(overrideContentType(entity), ack)
      def overrideContentType(entity: HttpEntity) =
        entity.flatMap { case HttpBody(ct, buf) ⇒ HttpEntity(contentType, buf) }
    }
}

/**
 * A convenience helper base class simplifying the construction of MarshallingContext that
 * wrap another MarshallingContext with some extra logic.
 */
class DelegatingMarshallingContext(underlying: MarshallingContext) extends MarshallingContext {
  def tryAccept(contentType: ContentType) = underlying.tryAccept(contentType)
  def rejectMarshalling(supported: Seq[ContentType]): Unit = { underlying.rejectMarshalling(supported) }
  def marshalTo(entity: HttpEntity): Unit = { underlying.marshalTo(entity) }
  def handleError(error: Throwable): Unit = { underlying.handleError(error) }
  def startChunkedMessage(entity: HttpEntity, ack: Option[Any] = None)(implicit sender: ActorRef) =
    underlying.startChunkedMessage(entity, ack)
}