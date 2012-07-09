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

package cc.spray.httpx

import cc.spray.httpx.unmarshalling._
import cc.spray.httpx.encoding.Decoder
import cc.spray.http._


trait ResponseTransformation {

  def decode(decoder: Decoder): ResponseTransformer = { response =>
    if (response.encoding == decoder.encoding) decoder.decode(response) else response
  }

  def unmarshal[T :Unmarshaller]: HttpResponse => T = { response =>
    if (response.status.isSuccess)
      response.entity.as[T] match {
        case Right(value) => value
        case Left(error) => throw new PipelineException(error.toString)
      }
    else throw new UnsuccessfulResponseException(response.status)
  }

  implicit def concatResponseTransformers(f: ResponseTransformer) = new ConcatenatedResponseTransformer(f)
  class ConcatenatedResponseTransformer(f: ResponseTransformer) extends ResponseTransformer {
    def apply(response: HttpResponse) = f(response)
    def ~> (g: ResponseTransformer) = new ConcatenatedResponseTransformer(g compose f)
  }
}

object ResponseTransformation extends ResponseTransformation

class PipelineException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
class UnsuccessfulResponseException(val responseStatus: StatusCode) extends RuntimeException