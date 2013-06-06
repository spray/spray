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

package spray.httpx

import akka.dispatch.{ ExecutionContext, Future }
import akka.event.LoggingAdapter
import spray.httpx.unmarshalling._
import spray.httpx.encoding.Decoder
import spray.http._

trait ResponseTransformation {
  import ResponseTransformation.ResponseTransformer

  def decode(decoder: Decoder): ResponseTransformer = { response ⇒
    if (response.encoding == decoder.encoding) decoder.decode(response) else response
  }

  def unmarshal[T: Unmarshaller]: HttpResponse ⇒ T = { response ⇒
    if (response.status.isSuccess)
      response.entity.as[T] match {
        case Right(value) ⇒ value
        case Left(error)  ⇒ throw new PipelineException(error.toString)
      }
    else throw new UnsuccessfulResponseException(response)
  }

  def logResponse(log: LoggingAdapter): HttpResponse ⇒ HttpResponse =
    logResponse { response ⇒ log.debug(response.toString) }

  def logResponse(logFun: HttpResponse ⇒ Unit): HttpResponse ⇒ HttpResponse = { response ⇒
    logFun(response)
    response
  }

  implicit def pimpWithResponseTransformation[A, B](f: A ⇒ B) = new PimpedResponseTransformer(f)
  class PimpedResponseTransformer[A, B](f: A ⇒ B) extends (A ⇒ B) {
    def apply(input: A) = f(input)
    def ~>[AA, BB, R](g: AA ⇒ BB)(implicit aux: TransformerAux[A, B, AA, BB, R]) =
      new PimpedResponseTransformer[A, R](aux(f, g))
  }
}

object ResponseTransformation extends ResponseTransformation {
  type ResponseTransformer = HttpResponse ⇒ HttpResponse
}

trait TransformerAux[A, B, AA, BB, R] {
  def apply(f: A ⇒ B, g: AA ⇒ BB): A ⇒ R
}

object TransformerAux {
  implicit def aux1[A, B, C] = new TransformerAux[A, B, B, C, C] {
    def apply(f: A ⇒ B, g: B ⇒ C) = f andThen g
  }
  implicit def aux2[A, B, C](implicit ec: ExecutionContext) =
    new TransformerAux[A, Future[B], B, C, Future[C]] {
      def apply(f: A ⇒ Future[B], g: B ⇒ C) = f(_).map(g)
    }
  implicit def aux3[A, B, C](implicit ec: ExecutionContext) =
    new TransformerAux[A, Future[B], B, Future[C], Future[C]] {
      def apply(f: A ⇒ Future[B], g: B ⇒ Future[C]) = f(_).flatMap(g)
    }
}

class PipelineException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
class UnsuccessfulResponseException(val response: HttpResponse) extends RuntimeException("Status: " + response.status + "\n" +
  "Body: " + { if (response.entity.buffer.length < 1024) response.entity.asString else response.entity.buffer.length + " bytes" })
