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
    Marshaller[Option[T]] { (value, ctx) =>
      value match {
        case Some(v) => m(v, ctx)
        case None => ctx.marshalTo(EmptyEntity)
      }
    }

  implicit def eitherMarshaller[A, B](implicit ma: Marshaller[A], mb: Marshaller[B]) =
    Marshaller[Either[A, B]] { (value, ctx) =>
      value match {
        case Left(a) => ma(a, ctx)
        case Right(b) => mb(b, ctx)
      }
    }

  implicit def futureMarshaller[T](implicit m: Marshaller[T]) =
    Marshaller[Future[T]] { (value, ctx) =>
      value.onComplete {
        case Right(v) => m(v, ctx)
        case Left(error) => ctx.handleError(error)
      }
    }
}

object MetaMarshallers extends MetaMarshallers