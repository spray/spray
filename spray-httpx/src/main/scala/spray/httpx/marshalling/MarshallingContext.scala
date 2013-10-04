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

package spray.httpx.marshalling

import akka.actor.ActorRef
import spray.http._

trait MarshallingContext { self ⇒

  /**
   * Determines whether the given ContentType is acceptable.
   * If the given ContentType does not define a charset an accepted charset is selected, i.e. the method guarantees
   * that, if a ContentType instance is returned within the option, it will contain a defined charset.
   */
  def tryAccept(contentTypes: Seq[ContentType]): Option[ContentType]

  /**
   * Signals that the Marshaller rejects the marshalling request because
   * none of its target ContentTypes is accepted.
   */
  def rejectMarshalling(supported: Seq[ContentType])

  /**
   * Receives the HttpEntity produced by a Marshaller.
   * If any headers are given they will be added to the produced HttpMessage.
   */
  def marshalTo(entity: HttpEntity, headers: HttpHeader*)

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
  def startChunkedMessage(entity: HttpEntity, ack: Option[Any] = None,
                          headers: Seq[HttpHeader] = Nil)(implicit sender: ActorRef): ActorRef

  /**
   * Creates a new MarshallingContext based on this one, that overrides the ContentType of the produced entity
   * with the given one.
   */
  def withContentTypeOverriding(contentType: ContentType): MarshallingContext =
    new DelegatingMarshallingContext(self) {
      override def tryAccept(cts: Seq[ContentType]) =
        Some(if (contentType.isCharsetDefined) cts.head.withCharset(contentType.charset) else cts.head)
      override def marshalTo(entity: HttpEntity, headers: HttpHeader*): Unit =
        self.marshalTo(overrideContentType(entity), headers: _*)
      override def startChunkedMessage(entity: HttpEntity, ack: Option[Any], headers: Seq[HttpHeader])(implicit sender: ActorRef) =
        self.startChunkedMessage(overrideContentType(entity), ack, headers)
      private def overrideContentType(entity: HttpEntity) =
        entity.flatMap {
          case HttpEntity.NonEmpty(ct, data) ⇒
            val c =
              if (contentType.noCharsetDefined && ct.isCharsetDefined) contentType.withCharset(ct.charset)
              else contentType
            HttpEntity(c, data)
        }
    }
}

/**
 * A convenience helper base class simplifying the construction of MarshallingContext that
 * wrap another MarshallingContext with some extra logic.
 */
class DelegatingMarshallingContext(underlying: MarshallingContext) extends MarshallingContext {
  def tryAccept(contentTypes: Seq[ContentType]) = underlying.tryAccept(contentTypes)
  def rejectMarshalling(supported: Seq[ContentType]): Unit = underlying.rejectMarshalling(supported)
  def marshalTo(entity: HttpEntity, headers: HttpHeader*): Unit = underlying.marshalTo(entity, headers: _*)
  def handleError(error: Throwable): Unit = underlying.handleError(error)
  def startChunkedMessage(entity: HttpEntity, ack: Option[Any] = None, headers: Seq[HttpHeader] = Nil)(implicit sender: ActorRef) =
    underlying.startChunkedMessage(entity, ack, headers)
}

trait ToResponseMarshallingContext { self ⇒
  /**
   * Determines whether the given ContentType is acceptable.
   * If the given ContentType does not define a charset an accepted charset is selected, i.e. the method guarantees
   * that, if a ContentType instance is returned within the option, it will contain a defined charset.
   */
  def tryAccept(contentTypes: Seq[ContentType]): Option[ContentType]

  /**
   * Signals that the Marshaller rejects the marshalling request because
   * none of its target ContentTypes is accepted.
   */
  def rejectMarshalling(supported: Seq[ContentType])

  /**
   * Receives the HttpResponse produced by a Marshaller.
   */
  def marshalTo(response: HttpResponse)

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
  def startChunkedMessage(response: HttpResponse, ack: Option[Any] = None)(implicit sender: ActorRef): ActorRef

  /**
   * Creates a new ToResponseMarshallingContext based on this one, that overrides the ContentType of the produced entity
   * with the given one.
   */
  def withContentTypeOverriding(contentType: ContentType): ToResponseMarshallingContext =
    new ToResponseMarshallingContext {
      def tryAccept(cts: Seq[ContentType]): Option[ContentType] =
        Some(if (contentType.isCharsetDefined) cts.head.withCharset(contentType.charset) else cts.head)
      def rejectMarshalling(supported: Seq[ContentType]): Unit = self.rejectMarshalling(supported)
      def marshalTo(response: HttpResponse): Unit = self.marshalTo(overrideContentType(response))
      def handleError(error: Throwable): Unit = self.handleError(error)
      def startChunkedMessage(response: HttpResponse, ack: Option[Any])(implicit sender: ActorRef): ActorRef =
        self.startChunkedMessage(overrideContentType(response), ack)
      private def overrideContentType(response: HttpResponse) =
        response.withEntity {
          response.entity.flatMap {
            case HttpEntity.NonEmpty(ct, data) ⇒
              val c =
                if (contentType.noCharsetDefined && ct.isCharsetDefined) contentType.withCharset(ct.charset)
                else contentType
              HttpEntity(c, data)
          }
        }
    }
}