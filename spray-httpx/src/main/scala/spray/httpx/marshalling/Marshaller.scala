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

//# source-quote
trait Marshaller[-T] {
  def apply(value: T, ctx: MarshallingContext)
}
//#

object Marshaller extends BasicMarshallers
    with MetaMarshallers
    with MultipartMarshallers {

  def apply[T](f: (T, MarshallingContext) ⇒ Unit): Marshaller[T] =
    new Marshaller[T] {
      def apply(value: T, ctx: MarshallingContext): Unit = f(value, ctx)
    }

  def of[T](marshalTo: ContentType*)(f: (T, ContentType, MarshallingContext) ⇒ Unit): Marshaller[T] =
    new Marshaller[T] {
      def apply(value: T, ctx: MarshallingContext): Unit =
        ctx.tryAccept(marshalTo) match {
          case Some(contentType) ⇒ f(value, contentType, ctx)
          case None              ⇒ ctx.rejectMarshalling(marshalTo)
        }
    }

  def delegate[A, B](marshalTo: ContentType*) = new MarshallerDelegation[A, B](marshalTo)

  class MarshallerDelegation[A, B](marshalTo: Seq[ContentType]) {
    def apply(f: A ⇒ B)(implicit mb: Marshaller[B]): Marshaller[A] = apply((a, ct) ⇒ f(a))
    def apply(f: (A, ContentType) ⇒ B)(implicit mb: Marshaller[B]): Marshaller[A] =
      Marshaller.of[A](marshalTo: _*) { (value, contentType, ctx) ⇒
        mb(f(value, contentType), ctx.withContentTypeOverriding(contentType))
      }
  }
}

trait ToResponseMarshaller[-T] {
  def apply(value: T, ctx: ToResponseMarshallingContext)
}

object ToResponseMarshaller extends LowPriorityToResponseMarshallerImplicits with MetaToResponseMarshallers {

  def fromMarshaller[T](status: StatusCode = StatusCodes.OK, headers: Seq[HttpHeader] = Nil)(implicit m: Marshaller[T]): ToResponseMarshaller[T] =
    new ToResponseMarshaller[T] {
      def apply(value: T, ctx: ToResponseMarshallingContext): Unit = {
        val mCtx = new MarshallingContext {
          def tryAccept(contentTypes: Seq[ContentType]): Option[ContentType] = ctx.tryAccept(contentTypes)
          def handleError(error: Throwable): Unit = ctx.handleError(error)
          def marshalTo(entity: HttpEntity, hs: HttpHeader*): Unit =
            ctx.marshalTo(HttpResponse(status, entity, (headers ++ hs).toList))
          def rejectMarshalling(supported: Seq[ContentType]): Unit = ctx.rejectMarshalling(supported)
          def startChunkedMessage(entity: HttpEntity, ack: Option[Any], hs: Seq[HttpHeader])(implicit sender: ActorRef): ActorRef =
            ctx.startChunkedMessage(HttpResponse(status, entity, (headers ++ hs).toList), ack)
        }
        m(value, mCtx)
      }
    }

  def apply[T](f: (T, ToResponseMarshallingContext) ⇒ Unit): ToResponseMarshaller[T] =
    new ToResponseMarshaller[T] {
      def apply(value: T, ctx: ToResponseMarshallingContext): Unit = f(value, ctx)
    }

  def of[T](marshalTo: ContentType*)(f: (T, ContentType, ToResponseMarshallingContext) ⇒ Unit): ToResponseMarshaller[T] =
    new ToResponseMarshaller[T] {
      def apply(value: T, ctx: ToResponseMarshallingContext): Unit =
        ctx.tryAccept(marshalTo) match {
          case Some(contentType) ⇒ f(value, contentType, ctx)
          case None              ⇒ ctx.rejectMarshalling(marshalTo)
        }
    }

  def delegate[A, B](marshalTo: ContentType*) = new MarshallerDelegation[A, B](marshalTo)

  class MarshallerDelegation[A, B](marshalTo: Seq[ContentType]) {
    def apply(f: A ⇒ B)(implicit mb: ToResponseMarshaller[B]): ToResponseMarshaller[A] = apply((a, ct) ⇒ f(a))
    def apply(f: (A, ContentType) ⇒ B)(implicit mb: ToResponseMarshaller[B]): ToResponseMarshaller[A] =
      ToResponseMarshaller.of[A](marshalTo: _*) { (value, contentType, ctx) ⇒
        mb(f(value, contentType), ctx.withContentTypeOverriding(contentType))
      }
  }
}

sealed abstract class LowPriorityToResponseMarshallerImplicits {
  implicit def liftMarshaller[T](implicit m: Marshaller[T]): ToResponseMarshaller[T] =
    ToResponseMarshaller.fromMarshaller()
}