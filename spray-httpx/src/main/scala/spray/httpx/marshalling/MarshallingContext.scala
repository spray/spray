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

trait MarshallingContext {

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
  def withContentTypeOverriding(contentType: ContentType): MarshallingContext = {
    val overridingCtx = withEntityMapped {
      case HttpEntity.Empty ⇒ HttpEntity.Empty
      case HttpEntity.NonEmpty(ct, data) ⇒
        val c =
          if (contentType.noCharsetDefined && ct.isCharsetDefined) contentType.withCharset(ct.charset)
          else contentType
        HttpEntity(c, data)
    }
    new DelegatingMarshallingContext(overridingCtx) {
      override def tryAccept(cts: Seq[ContentType]) =
        Some(if (contentType.isCharsetDefined) cts.head.withCharset(contentType.charset) else cts.head)
    }
  }

  /**
   * Creates a new MarshallingContext based on this one, that transforms the produced entity using
   * the given function.
   */
  def withEntityMapped(f: HttpEntity ⇒ HttpEntity): MarshallingContext =
    new DelegatingMarshallingContext(this) {
      override def marshalTo(entity: HttpEntity, headers: HttpHeader*): Unit =
        underlying.marshalTo(f(entity), headers: _*)
      override def startChunkedMessage(entity: HttpEntity, ack: Option[Any], headers: Seq[HttpHeader])(implicit sender: ActorRef) =
        underlying.startChunkedMessage(f(entity), ack, headers)
    }
}

/**
 * A convenience helper base class simplifying the construction of a MarshallingContext that
 * wraps another MarshallingContext with some extra logic.
 */
class DelegatingMarshallingContext(protected val underlying: MarshallingContext) extends MarshallingContext {
  def tryAccept(contentTypes: Seq[ContentType]) = underlying.tryAccept(contentTypes)
  def rejectMarshalling(supported: Seq[ContentType]): Unit = underlying.rejectMarshalling(supported)
  def marshalTo(entity: HttpEntity, headers: HttpHeader*): Unit = underlying.marshalTo(entity, headers: _*)
  def handleError(error: Throwable): Unit = underlying.handleError(error)
  def startChunkedMessage(entity: HttpEntity, ack: Option[Any] = None, headers: Seq[HttpHeader] = Nil)(implicit sender: ActorRef) =
    underlying.startChunkedMessage(entity, ack, headers)
}

trait ToResponseMarshallingContext {
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
  def withContentTypeOverriding(contentType: ContentType): ToResponseMarshallingContext = {
    val overridingCtx = withResponseMapped { response ⇒
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
    new DelegatingToResponseMarshallingContext(overridingCtx) {
      override def tryAccept(cts: Seq[ContentType]) =
        Some(if (contentType.isCharsetDefined) cts.head.withCharset(contentType.charset) else cts.head)
    }
  }

  /**
   * Creates a new ToResponseMarshallingContext based on this one, that transforms the produced response using
   * the given function.
   */
  def withResponseMapped(f: HttpResponse ⇒ HttpResponse): ToResponseMarshallingContext =
    new DelegatingToResponseMarshallingContext(this) {
      override def marshalTo(response: HttpResponse): Unit = underlying.marshalTo(f(response))
      override def startChunkedMessage(response: HttpResponse, ack: Option[Any])(implicit sender: ActorRef): ActorRef =
        underlying.startChunkedMessage(f(response), ack)
    }
}

/**
 * A convenience helper base class simplifying the construction of a ToResponseMarshallingContext that
 * wraps another ToResponseMarshallingContext with some extra logic.
 */
class DelegatingToResponseMarshallingContext(protected val underlying: ToResponseMarshallingContext)
    extends ToResponseMarshallingContext {
  def tryAccept(contentTypes: Seq[ContentType]) = underlying.tryAccept(contentTypes)
  def rejectMarshalling(supported: Seq[ContentType]): Unit = underlying.rejectMarshalling(supported)
  def marshalTo(response: HttpResponse): Unit = underlying.marshalTo(response)
  def handleError(error: Throwable): Unit = underlying.handleError(error)
  def startChunkedMessage(response: HttpResponse, ack: Option[Any])(implicit sender: ActorRef): ActorRef =
    underlying.startChunkedMessage(response, ack)
}