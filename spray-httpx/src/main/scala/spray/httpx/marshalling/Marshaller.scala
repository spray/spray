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

import scala.util.control.NonFatal
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
      def apply(value: T, ctx: MarshallingContext): Unit =
        try f(value, ctx)
        catch {
          case NonFatal(e) ⇒ ctx.handleError(e)
        }
    }

  def of[T](marshalTo: ContentType*)(f: (T, ContentType, MarshallingContext) ⇒ Unit): Marshaller[T] =
    new Marshaller[T] {
      def apply(value: T, ctx: MarshallingContext): Unit =
        try {
          ctx.tryAccept(marshalTo) match {
            case Some(contentType) ⇒ f(value, contentType, ctx)
            case None              ⇒ ctx.rejectMarshalling(marshalTo)
          }
        } catch {
          case NonFatal(e) ⇒ ctx.handleError(e)
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

  def compose[U](f: U ⇒ T): ToResponseMarshaller[U] = ToResponseMarshaller((value, ctx) ⇒ apply(f(value), ctx))
}

object ToResponseMarshaller extends BasicToResponseMarshallers
    with MetaToResponseMarshallers
    with LowPriorityToResponseMarshallerImplicits {
  def fromMarshaller[T](status: StatusCode = StatusCodes.OK, headers: Seq[HttpHeader] = Nil)(implicit m: Marshaller[T]): ToResponseMarshaller[T] =
    fromStatusCodeAndHeadersAndT.compose(t ⇒ (status, headers, t))

  def apply[T](f: (T, ToResponseMarshallingContext) ⇒ Unit): ToResponseMarshaller[T] =
    new ToResponseMarshaller[T] {
      def apply(value: T, ctx: ToResponseMarshallingContext): Unit =
        try f(value, ctx)
        catch {
          case NonFatal(e) ⇒ ctx.handleError(e)
        }
    }

  def of[T](marshalTo: ContentType*)(f: (T, ContentType, ToResponseMarshallingContext) ⇒ Unit): ToResponseMarshaller[T] =
    new ToResponseMarshaller[T] {
      def apply(value: T, ctx: ToResponseMarshallingContext): Unit =
        try {
          ctx.tryAccept(marshalTo) match {
            case Some(contentType) ⇒ f(value, contentType, ctx)
            case None              ⇒ ctx.rejectMarshalling(marshalTo)
          }
        } catch {
          case NonFatal(e) ⇒ ctx.handleError(e)
        }
    }

  def oneOf[T](marshalTo: ContentType*)(marshallers: ToResponseMarshaller[T]*): ToResponseMarshaller[T] =
    ToResponseMarshaller.of[T](marshalTo: _*) { (t, tpe, ctx) ⇒
      def tryNext(marshallers: List[ToResponseMarshaller[T]], previouslySupported: Set[ContentType]): Unit = marshallers match {
        case head :: tail ⇒
          head(t, new ToResponseMarshallingContext {
            def tryAccept(contentTypes: Seq[ContentType]): Option[ContentType] = ctx.tryAccept(contentTypes)
            def rejectMarshalling(supported: Seq[ContentType]): Unit = tryNext(tail, previouslySupported ++ supported)
            def marshalTo(response: HttpResponse): Unit = ctx.marshalTo(response)
            def handleError(error: Throwable): Unit = ctx.handleError(error)
            def startChunkedMessage(response: HttpResponse, ack: Option[Any])(implicit sender: ActorRef): ActorRef =
              ctx.startChunkedMessage(response, ack)(sender)
          })
        case Nil ⇒ ctx.rejectMarshalling(previouslySupported.toSeq)
      }
      tryNext(marshallers.toList, Set.empty)
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

sealed trait LowPriorityToResponseMarshallerImplicits {
  implicit def liftMarshallerConversion[T](m: Marshaller[T]): ToResponseMarshaller[T] = liftMarshaller(m)
  implicit def liftMarshaller[T](implicit m: Marshaller[T]): ToResponseMarshaller[T] =
    ToResponseMarshaller.fromMarshaller()
}

/** Something that can later be marshalled into a response */
trait ToResponseMarshallable {
  def marshal(ctx: ToResponseMarshallingContext): Unit
}
object ToResponseMarshallable {
  implicit def isMarshallable[T](value: T)(implicit marshaller: ToResponseMarshaller[T]): ToResponseMarshallable =
    new ToResponseMarshallable {
      def marshal(ctx: ToResponseMarshallingContext): Unit = marshaller(value, ctx)
    }
  implicit def marshallableIsMarshallable: ToResponseMarshaller[ToResponseMarshallable] =
    new ToResponseMarshaller[ToResponseMarshallable] {
      def apply(value: ToResponseMarshallable, ctx: ToResponseMarshallingContext): Unit = value.marshal(ctx)
    }
}
