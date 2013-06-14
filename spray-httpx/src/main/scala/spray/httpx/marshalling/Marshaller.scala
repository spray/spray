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

import spray.http.ContentType
import spray.util._

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
      def apply(value: T, ctx: MarshallingContext): Unit = {
        f(value, ctx)
      }
    }

  def of[T](marshalTo: ContentType*)(f: (T, ContentType, MarshallingContext) ⇒ Unit): Marshaller[T] =
    new Marshaller[T] {
      def apply(value: T, ctx: MarshallingContext): Unit = {
        marshalTo.mapFind(ctx.tryAccept) match {
          case Some(contentType) ⇒ f(value, contentType, ctx)
          case None              ⇒ ctx.rejectMarshalling(marshalTo)
        }
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