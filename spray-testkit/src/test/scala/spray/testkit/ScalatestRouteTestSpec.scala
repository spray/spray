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

package spray.testkit

import org.scalatest.FreeSpec
import org.scalatest.matchers.MustMatchers
import spray.routing.{MethodRejection, RequestContext, Directives}
import spray.http._
import HttpMethods._
import MediaTypes._
import HttpCharsets._
import StatusCodes._
import HttpHeaders._


class ScalatestRouteTestSpec extends FreeSpec with MustMatchers with Directives with ScalatestRouteTest {

  "The ScalatestRouteTest should support" - {

    "the most simple and direct route test" in {
      Get() ~> {
        (_: RequestContext).complete(HttpResponse())
      } ~> (_.response) must be === HttpResponse()
    }

    "a test using a directive and some checks" in {
      val pinkHeader = RawHeader("Fancy", "pink")
      Get() ~> addHeader(pinkHeader) ~> {
        respondWithHeader(pinkHeader) { complete("abc") }
      } ~> check {
        status must be === OK
        body must be === HttpBody(ContentType(`text/plain`, `ISO-8859-1`), "abc")
        header("Fancy") must be === Some(pinkHeader)
      }
    }

    "proper rejection collection" in {
      Post("/abc", "content") ~> {
        (get | put) {
          complete("naah")
        }
      } ~> check {
        rejections must be === List(MethodRejection(GET), MethodRejection(PUT))
      }
    }
  }

}