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

import org.specs2.mutable.Specification
import akka.dispatch.{ Promise, Future }
import akka.actor.ActorSystem
import encoding.Gzip
import spray.http._
import spray.util._
import HttpHeaders._

class ResponseTransformationSpec extends Specification with RequestBuilding with ResponseTransformation {
  implicit val system = ActorSystem()
  import system.dispatcher
  type SendReceive = HttpRequest ⇒ Future[HttpResponse]

  "MessagePipelining" should {
    "work correctly for simple requests" in {
      val pipeline = report
      pipeline(Get("/abc")).await === HttpResponse(200, "GET|/abc|")
    }

    "support marshalling" in {
      val pipeline = report
      pipeline(Get("/abc", "Hello")).await === HttpResponse(200, "GET|/abc|Hello")
    }

    "support unmarshalling" in {
      val pipeline = report ~> unmarshal[String]
      pipeline(Get("/abc")).await === "GET|/abc|"
    }

    "support request compression" in {
      val pipeline = encode(Gzip) ~> reportDecoding
      pipeline(Get("/abc", "Hello")).await === HttpResponse(200, "GET|/abc|Hello")
    }

    "support response decompression" in {
      val pipeline = encode(Gzip) ~> echo ~> decode(Gzip)
      pipeline(Get("/abc", "Hello")).await === HttpResponse(200, "Hello", List(`Content-Encoding`(HttpEncodings.gzip)))
    }

    "support request authentication" in {
      val pipeline = addCredentials(BasicHttpCredentials("bob", "1234")) ~> authenticatedEcho
      pipeline(Get()).await === HttpResponse(200)
    }

    "allow success for any status codes representing success" in {
      val pipeline = report ~> ((_: HttpResponse).copy(status = 201)) ~> unmarshal[String]
      pipeline(Get("/abc")).await === "GET|/abc|"
    }

    "throw an Exception when unmarshalling client error responses" in {
      val pipeline = echo ~> ((_: HttpResponse).copy(status = 400)) ~> unmarshal[String]
      pipeline(Get("/", "XXX")).await must throwAn(
        new UnsuccessfulResponseException(HttpResponse(StatusCodes.BadRequest, entity = "XXX")))
    }

    "throw an Exception when unmarshalling server error responses" in {
      val pipeline = echo ~> ((_: HttpResponse).copy(status = 500)) ~> unmarshal[String]
      pipeline(Get("/", "XXX")).await must throwAn(
        new UnsuccessfulResponseException(HttpResponse(StatusCodes.InternalServerError, entity = "XXX")))
    }
  }

  step(system.shutdown())

  val report: SendReceive = { request ⇒
    import request._
    Promise.successful(HttpResponse(200, method + "|" + uri + "|" + entity.asString)).future
  }

  val reportDecoding: SendReceive = request ⇒ {
    val decoded = Gzip.decode(request)
    import decoded._
    Promise.successful(HttpResponse(200, method + "|" + uri + "|" + entity.asString)).future
  }

  val echo: SendReceive = request ⇒ Promise.successful(
    HttpResponse(200, request.entity, request.headers.filter(_.isInstanceOf[`Content-Encoding`]))).future

  val authenticatedEcho: SendReceive = request ⇒ Promise.successful(
    HttpResponse(
      status = request.headers
        .collect { case Authorization(BasicHttpCredentials("bob", "1234")) ⇒ StatusCodes.OK }
        .headOption.getOrElse(StatusCodes.Forbidden))).future

}
