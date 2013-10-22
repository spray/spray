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

import akka.dispatch.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import spray.http._

trait MetaToResponseMarshallers {

  implicit def optionMarshaller[T](implicit m: ToResponseMarshaller[T]) =
    ToResponseMarshaller[Option[T]] { (value, ctx) ⇒
      value match {
        case Some(v) ⇒ m(v, ctx)
        case None    ⇒ ctx.marshalTo(HttpResponse(StatusCodes.NotFound))
      }
    }

  implicit def eitherMarshaller[A, B](implicit ma: ToResponseMarshaller[A], mb: ToResponseMarshaller[B]) =
    ToResponseMarshaller[Either[A, B]] { (value, ctx) ⇒
      value match {
        case Left(a)  ⇒ ma(a, ctx)
        case Right(b) ⇒ mb(b, ctx)
      }
    }

  implicit def futureMarshaller[T](implicit m: ToResponseMarshaller[T], ec: ExecutionContext) =
    ToResponseMarshaller[Future[T]] { (value, ctx) ⇒
      value.onComplete {
        case Right(v)    ⇒ m(v, ctx)
        case Left(error) ⇒ ctx.handleError(error)
      }
    }

  implicit def tryMarshaller[T](implicit m: ToResponseMarshaller[T]) =
    ToResponseMarshaller[Either[Throwable, T]] { (value, ctx) ⇒
      value match {
        case Right(v) ⇒ m(v, ctx)
        case Left(t)  ⇒ ctx.handleError(t)
      }
    }
}

object MetaToResponseMarshallers extends MetaToResponseMarshallers