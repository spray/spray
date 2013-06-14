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

import akka.event.LoggingAdapter
import spray.httpx.unmarshalling._
import spray.httpx.encoding.Decoder
import spray.http._

trait ResponseTransformation extends TransformerPipelineSupport {
  type ResponseTransformer = HttpResponse ⇒ HttpResponse

  def decode(decoder: Decoder): ResponseTransformer =
    response ⇒ if (response.encoding == decoder.encoding) decoder.decode(response) else response

  def unmarshal[T: Unmarshaller]: HttpResponse ⇒ T =
    response ⇒
      if (response.status.isSuccess)
        response.entity.as[T] match {
          case Right(value) ⇒ value
          case Left(error)  ⇒ throw new PipelineException(error.toString)
        }
      else throw new UnsuccessfulResponseException(response)

  def logResponse(log: LoggingAdapter) = logValue[HttpResponse](log)

  def logResponse(logFun: HttpResponse ⇒ Unit) = logValue[HttpResponse](logFun)
}

object ResponseTransformation extends ResponseTransformation

class PipelineException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
class UnsuccessfulResponseException(val response: HttpResponse) extends RuntimeException(s"Status: ${response.status}\n" +
  s"Body: ${if (response.entity.buffer.length < 1024) response.entity.asString else response.entity.buffer.length + " bytes"}")