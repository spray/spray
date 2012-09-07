/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.httpx.marshalling

import akka.dispatch.Future
import cc.spray.http._


trait MetaMarshallers {

  implicit def optionMarshaller[T](implicit m: Marshaller[T]) =
    new Marshaller[Option[T]] {
      def apply(selector: ContentTypeSelector) = m(selector).right.map { marshalling =>
        new Marshalling[Option[T]] {
          def apply(value: Option[T], ctx: MarshallingContext) {
            value match {
              case Some(v) => marshalling.runSafe(v, ctx)
              case None => ctx.marshalTo(EmptyEntity)
            }
          }
        }
      }
    }

  implicit def eitherMarshaller[A, B](implicit ma: Marshaller[A], mb: Marshaller[B]) =
    new Marshaller[Either[A, B]] {
      def apply(selector: ContentTypeSelector) = {
        ma(selector).right.flatMap { marshallingA =>
          mb(selector).right.map { marshallingB =>
            new Marshalling[Either[A, B]] {
              def apply(value: Either[A, B], ctx: MarshallingContext) {
                value match {
                  case Right(b) => marshallingB.runSafe(b, ctx)
                  case Left(a) => marshallingA.runSafe(a, ctx)
                }
              }
            }
          }
        }
      }
    }

  implicit def futureMarshaller[T](implicit m: Marshaller[T]) =
    new Marshaller[Future[T]] {
      def apply(selector: ContentTypeSelector) = m(selector).right.map { marshalling =>
        new Marshalling[Future[T]] {
          def apply(value: Future[T], ctx: MarshallingContext) {
            value.onComplete {
              case Right(v) => marshalling.runSafe(v, ctx)
              case Left(error) => ctx.handleError(error)
            }
          }
        }
      }
    }
}

object MetaMarshallers extends MetaMarshallers