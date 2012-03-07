/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package client

import http._
import typeconversion._
import akka.dispatch.Future
import encoding.{Decoder, Encoder}

trait MessagePipelining {

  type SendReceive = HttpRequest => Future[HttpResponse]

  def simpleRequest: SimpleRequest[Nothing] => HttpRequest = { simpleRequest =>
    HttpRequest(
      method = simpleRequest.method,
      uri = simpleRequest.uri
    )
  }

  def simpleRequest[T :Marshaller]: SimpleRequest[T] => HttpRequest = {
    simpleRequest => HttpRequest(
      method = simpleRequest.method,
      uri = simpleRequest.uri,
      content = simpleRequest.content.map(_.toHttpContent)
    )
  }

  def encode(encoder: Encoder): HttpRequest => HttpRequest = encoder.encode(_)

  def addHeader(header: HttpHeader): HttpRequest => HttpRequest = req => req.withHeaders(header :: req.headers)

  def addHeaders(first: HttpHeader, more: HttpHeader*): HttpRequest => HttpRequest = addHeaders(first :: more.toList)

  def addHeaders(headers: List[HttpHeader]): HttpRequest => HttpRequest = req => req.withHeaders(headers ::: req.headers)

  def authenticate(credentials: BasicHttpCredentials) = addHeader(HttpHeaders.Authorization(credentials))

  def decode(decoder: Decoder) = transformResponse { response: HttpResponse =>
    if (response.encoding == decoder.encoding) decoder.decode[HttpResponse](response)
    else response
  }

  def unmarshal[T :Unmarshaller] = transformResponse { response: HttpResponse =>
    response.status match {
      case StatusCodes.OK => unmarshaller[T].apply(response.content) match {
        case Right(value) => value
        case Left(error) => throw new PipelineException(error.toString) // "unwrap" the error into the future
      }
      case status => throw new UnsuccessfulResponseException(status)
    }
  }

  def transformResponse[A, B](f: A => B): Future[A] => Future[B] = _.map(f)

  implicit def pimpFunction[A, B](f: A => B) = new PimpedFunction(f)
  class PimpedFunction[A, B](f: A => B) extends (A => B) {
    def apply(a: A) = f(a)
    def ~> [C](g: B => C): A => C = g compose f
  }
}

object MessagePipelining extends MessagePipelining
