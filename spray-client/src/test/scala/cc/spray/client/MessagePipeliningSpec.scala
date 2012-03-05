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

import akka.dispatch._
import http._
import org.specs2.Specification
import typeconversion.{DefaultMarshallers, DefaultUnmarshallers}
import encoding.Gzip
import HttpHeaders._
import HttpEncodings._

class MessagePipeliningSpec extends Specification
  with MessagePipelining with DefaultMarshallers with DefaultUnmarshallers { def is =

  "MessagePipelining should"                                ^
  "work correctly for simple requests"                      ! testSimple^
  "support marshalling"                                     ! testMarshalling^
  "support unmarshalling"                                   ! testUnmarshalling^
  "support request compression"                             ! testCompression^
  "support response decompression"                          ! testDecompression^
  "support request authentication"                          ! testAuthentication^
  "throw an Exception when unmarshalling non-200 responses" ! testUnsuccessfulUnmarshalling^
                                                            end

  val report: SendReceive = { request =>
    import request._
    completed(HttpResponse(200, method + "|" + uri + "|" + content.map(_.as[String])))
  }

  val reportDecoding: SendReceive = request => completed {
    val decoded = Gzip.decode(request)
    import decoded._
    HttpResponse(200, method + "|" + uri + "|" + content.map(_.as[String]))
  }

  val echo: SendReceive = request => completed {
    HttpResponse(200, request.headers.filter(_.isInstanceOf[`Content-Encoding`]), request.content.get)
  }

  val authenticatedEcho: SendReceive = request => completed {
    HttpResponse(
      status = request.headers
        .collect { case Authorization(BasicHttpCredentials("bob", "1234")) => StatusCodes.OK }
        .headOption.getOrElse(StatusCodes.Forbidden)
    )
  }

  def completed(response: HttpResponse) =
    new DefaultCompletableFuture[HttpResponse](Long.MaxValue).completeWithResult(response)

  def testSimple = {
    val pipeline = simpleRequest ~> report
    pipeline(Get("/abc")).get mustEqual HttpResponse(200, "GET|/abc|None")
  }

  def testMarshalling = {
    val pipeline = simpleRequest[String] ~> report
    pipeline(Get("/abc", "Hello")).get mustEqual HttpResponse(200, "GET|/abc|Some(Right(Hello))")
  }

  def testUnmarshalling = {
    val pipeline = simpleRequest ~> report ~> unmarshal[String]
    pipeline(Get("/abc")).get mustEqual "GET|/abc|None"
  }

  def testCompression = {
    val pipeline = simpleRequest[String] ~> encode(Gzip) ~> reportDecoding
    pipeline(Get("/abc", "Hello")).get mustEqual HttpResponse(200, "GET|/abc|Some(Right(Hello))")
  }

  def testDecompression = {
    val pipeline = simpleRequest[String] ~> encode(Gzip) ~> echo ~> decode(Gzip)
    pipeline(Get("/abc", "Hello")).get mustEqual HttpResponse(200, List(`Content-Encoding`(gzip)), "Hello")
  }

  def testAuthentication = {
    val pipeline = simpleRequest ~> authenticate(BasicHttpCredentials("bob", "1234")) ~> authenticatedEcho
    pipeline(Get()).get mustEqual HttpResponse(200)
  }

  def testUnsuccessfulUnmarshalling = {
    val pipeline = simpleRequest[String] ~> echo ~> transformResponse(_.copy(status = 500)) ~> unmarshal[String]
    pipeline(Get("/", "XXX")).get must throwAn(new UnsuccessfulResponseException(StatusCodes.InternalServerError))
  }
}