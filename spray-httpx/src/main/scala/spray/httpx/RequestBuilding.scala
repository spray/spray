/*
 * Copyright (C) 2011-2012 spray.io
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

import spray.httpx.encoding.Encoder
import spray.httpx.marshalling._
import spray.http.parser.HttpParser
import spray.http._
import HttpMethods._
import HttpHeaders._


trait RequestBuilding {
  import RequestBuilding.RequestTransformer

  private[httpx] sealed abstract class RequestBuilder {
    def method: HttpMethod
    def apply(): HttpRequest = apply("/")
    def apply(uri: String): HttpRequest = apply[String](uri, None)
    def apply[T :Marshaller](uri: String, content: T): HttpRequest = apply(uri, Some(content))
    def apply[T :Marshaller](uri: String, content: Option[T]): HttpRequest = {
      HttpRequest(method, uri,
        entity = content match {
          case None => EmptyEntity
          case Some(value) => marshal(value) match {
            case Right(entity) => entity
            case Left(error) => throw error
          }
        }
      )
    }
  }

  object Get    extends RequestBuilder { def method = GET }
  object Post   extends RequestBuilder { def method = POST }
  object Put    extends RequestBuilder { def method = PUT }
  object Patch  extends RequestBuilder { def method = PATCH }
  object Delete extends RequestBuilder { def method = DELETE }

  def encode(encoder: Encoder): RequestTransformer = encoder.encode(_)

  def addHeader(header: HttpHeader): RequestTransformer = _.mapHeaders(header :: _)

  def addHeader(headerName: String, headerValue: String): RequestTransformer = {
    val rawHeader = RawHeader(headerName, headerValue)
    addHeader(HttpParser.parseHeader(rawHeader).left.flatMap(_ => Right(rawHeader)).right.get)
  }

  def addHeaders(first: HttpHeader, more: HttpHeader*): RequestTransformer = addHeaders(first :: more.toList)

  def addHeaders(headers: List[HttpHeader]): RequestTransformer = _.mapHeaders(headers ::: _)

  def addCredentials(credentials: BasicHttpCredentials) = addHeader(HttpHeaders.Authorization(credentials))

  implicit def request2TransformableHttpRequest(request: HttpRequest) = new TransformableHttpRequest(request)
  class TransformableHttpRequest(request: HttpRequest) {
    def ~> [T](f: HttpRequest => T) = f(request)
    def ~> (header: HttpHeader) = addHeader(header)(request)
  }
}

object RequestBuilding extends RequestBuilding {
  type RequestTransformer = HttpRequest => HttpRequest
}