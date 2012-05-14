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

package cc.spray.client

import cc.spray.http._
import cc.spray.typeconversion.{DefaultMarshallers, DefaultUnmarshallers}
import cc.spray.encoding.Gzip
import HttpHeaders._
import HttpEncodings._
import akka.dispatch.Promise
import akka.actor.ActorSystem
import cc.spray.util._
import org.specs2.mutable.Specification

class MessagePipeliningSpec extends Specification with MessagePipelining {
  import DefaultMarshallers._
  import DefaultUnmarshallers._

  implicit val system = ActorSystem()

  "MessagePipelining" should {
    "work correctly for simple requests" in {
      val pipeline = simpleRequest ~> report
      pipeline(Get("/abc")).await === HttpResponse(200, "GET|/abc|None")
    }

    "support marshalling" in {
      val pipeline = simpleRequest[String] ~> report
      pipeline(Get("/abc", "Hello")).await === HttpResponse(200, "GET|/abc|Some(Right(Hello))")
    }

    "support unmarshalling" in {
      val pipeline = simpleRequest ~> report ~> unmarshal[String]
      pipeline(Get("/abc")).await === "GET|/abc|None"
    }

    "support request compression" in {
      val pipeline = simpleRequest[String] ~> encode(Gzip) ~> reportDecoding
      pipeline(Get("/abc", "Hello")).await === HttpResponse(200, "GET|/abc|Some(Right(Hello))")
    }

    "support response decompression" in {
      val pipeline = simpleRequest[String] ~> encode(Gzip) ~> echo ~> decode(Gzip)
      pipeline(Get("/abc", "Hello")).await === HttpResponse(200, List(`Content-Encoding`(gzip)), "Hello")
    }

    "support request authentication" in {
      val pipeline = simpleRequest ~> authenticate(BasicHttpCredentials("bob", "1234")) ~> authenticatedEcho
      pipeline(Get()).await === HttpResponse(200)
    }

		"allow success for any status codes representing success" in {
      val pipeline = simpleRequest ~> report ~> transformResponse(_.copy(status = 201)) ~> unmarshal[String]
      pipeline(Get("/abc")).await === "GET|/abc|None"
    }

		"throw an Exception when unmarshalling client error responses" in {
      val pipeline = simpleRequest[String] ~> echo ~> transformResponse(_.copy(status = 400)) ~> unmarshal[String]
      pipeline(Get("/", "XXX")).await must throwAn(new UnsuccessfulResponseException(StatusCodes.BadRequest))
    }

    "throw an Exception when unmarshalling server error responses" in {
      val pipeline = simpleRequest[String] ~> echo ~> transformResponse(_.copy(status = 500)) ~> unmarshal[String]
      pipeline(Get("/", "XXX")).await must throwAn(new UnsuccessfulResponseException(StatusCodes.InternalServerError))
    }
  }

  step(system.shutdown())

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

  def completed(response: HttpResponse) = Promise.successful(response)

}