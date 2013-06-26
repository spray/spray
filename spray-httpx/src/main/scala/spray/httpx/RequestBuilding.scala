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
import spray.httpx.encoding.Encoder
import spray.httpx.marshalling._
import spray.http.parser.HttpParser
import spray.http._
import HttpMethods._
import HttpHeaders._

trait RequestBuilding extends TransformerPipelineSupport {
  type RequestTransformer = HttpRequest ⇒ HttpRequest

  class RequestBuilder(val method: HttpMethod) {
    def apply(): HttpRequest = apply("/")
    def apply(uri: String): HttpRequest = apply[String](uri, None)
    def apply[T: Marshaller](uri: String, content: T): HttpRequest = apply(uri, Some(content))
    def apply[T: Marshaller](uri: String, content: Option[T]): HttpRequest = apply(Uri(uri), content)
    def apply(uri: Uri): HttpRequest = apply[String](uri, None)
    def apply[T: Marshaller](uri: Uri, content: T): HttpRequest = apply(uri, Some(content))
    def apply[T: Marshaller](uri: Uri, content: Option[T]): HttpRequest =
      HttpRequest(method, uri,
        entity = content match {
          case None ⇒ EmptyEntity
          case Some(value) ⇒ marshal(value) match {
            case Right(entity) ⇒ entity
            case Left(error)   ⇒ throw error
          }
        })
  }

  val Get = new RequestBuilder(GET)
  val Post = new RequestBuilder(POST)
  val Put = new RequestBuilder(PUT)
  val Patch = new RequestBuilder(PATCH)
  val Delete = new RequestBuilder(DELETE)
  val Options = new RequestBuilder(OPTIONS)
  val Head = new RequestBuilder(HEAD)

  def encode(encoder: Encoder): RequestTransformer = encoder.encode(_)

  def addHeader(header: HttpHeader): RequestTransformer = _.mapHeaders(header :: _)

  def addHeader(headerName: String, headerValue: String): RequestTransformer = {
    val rawHeader = RawHeader(headerName, headerValue)
    addHeader(HttpParser.parseHeader(rawHeader).left.flatMap(_ ⇒ Right(rawHeader)).right.get)
  }

  def addHeaders(first: HttpHeader, more: HttpHeader*): RequestTransformer = addHeaders(first :: more.toList)

  def addHeaders(headers: List[HttpHeader]): RequestTransformer = _.mapHeaders(headers ::: _)

  def addCredentials(credentials: HttpCredentials) = addHeader(HttpHeaders.Authorization(credentials))

  def logRequest(log: LoggingAdapter) = logValue[HttpRequest](log)

  def logRequest(logFun: HttpRequest ⇒ Unit) = logValue[HttpRequest](logFun)

  implicit def header2AddHeader(header: HttpHeader): RequestTransformer = addHeader(header)
}

object RequestBuilding extends RequestBuilding