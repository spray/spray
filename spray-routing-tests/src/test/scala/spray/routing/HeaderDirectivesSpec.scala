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

package spray.routing

import spray.http._
import HttpHeaders._

class HeaderDirectivesSpec extends RoutingSpec {

  "The headerValuePF directive" should {
    val myHeaderValue = headerValuePF { case Connection(tokens) ⇒ tokens.head }

    "extract the respective header value if a matching request header is present" in {
      Get("/abc") ~> addHeader(Connection("close")) ~> myHeaderValue { echoComplete } ~> check {
        responseAs[String] === "close"
      }
    }

    "reject with an empty rejection set if no matching request header is present" in {
      Get("/abc") ~> myHeaderValue { echoComplete } ~> check { rejections === Nil }
    }

    "reject with a MalformedHeaderRejection if the extract function throws an exception" in {
      Get("/abc") ~> addHeader(Connection("close")) ~> {
        (headerValuePF { case _ ⇒ sys.error("Naah!") }) { echoComplete }
      } ~> check {
        rejection must beLike { case MalformedHeaderRejection("Connection", "Naah!", _) ⇒ ok }
      }
    }
  }

  "The headerValueByType directive" should {
    val route =
      headerValueByType[Origin]() { origin ⇒
        complete(s"The first origin was ${origin.originList.head}")
      }
    "extract a header if the type is matching" in {
      val originHeader = Origin(Seq(HttpOrigin("http://localhost:8080")))
      Get("abc") ~> originHeader ~> route ~> check {
        responseAs[String] === "The first origin was http://localhost:8080"
      }
    }
    "reject a request if no header of the given type is present" in {
      Get("abc") ~> route ~> check {
        rejection must beLike { case MissingHeaderRejection("Origin") ⇒ ok }
      }
    }
  }

  "The optionalHeaderValue directive" should {
    val myHeaderValue = optionalHeaderValue {
      case Connection(tokens) ⇒ Some(tokens.head)
      case _                  ⇒ None
    }

    "extract the respective header value if a matching request header is present" in {
      Get("/abc") ~> addHeader(Connection("close")) ~> myHeaderValue { echoComplete } ~> check {
        responseAs[String] === "Some(close)"
      }
    }

    "extract None if no matching request header is present" in {
      Get("/abc") ~> myHeaderValue { echoComplete } ~> check { responseAs[String] === "None" }
    }

    "reject with a MalformedHeaderRejection if the extract function throws an exception" in {
      Get("/abc") ~> addHeader(Connection("close")) ~> {
        val myHeaderValue = optionalHeaderValue { case _ ⇒ sys.error("Naaah!") }
        myHeaderValue { echoComplete }
      } ~> check {
        rejection must beLike { case MalformedHeaderRejection("Connection", "Naaah!", _) ⇒ ok }
      }
    }
  }

  "The optionalHeaderValueByType directive" should {
    val route =
      optionalHeaderValueByType[Origin]() {
        case Some(origin) ⇒ complete(s"The first origin was ${origin.originList.head}")
        case None         ⇒ complete("No Origin header found.")
      }
    "extract Some(header) if the type is matching" in {
      val originHeader = Origin(Seq(HttpOrigin("http://localhost:8080")))
      Get("abc") ~> originHeader ~> route ~> check {
        responseAs[String] === "The first origin was http://localhost:8080"
      }
    }
    "extract None if no header of the given type is present" in {
      Get("abc") ~> route ~> check {
        responseAs[String] === "No Origin header found."
      }
    }
  }
}