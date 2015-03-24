/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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
import org.scalatest.Matchers
import akka.testkit.TestProbe
import spray.routing.{ MethodRejection, RequestContext, Directives }
import spray.http._
import HttpMethods._
import MediaTypes._
import HttpCharsets._
import StatusCodes._
import HttpHeaders._

class ScalatestRouteTestSpec extends FreeSpec with Matchers with Directives with ScalatestRouteTest {

  "The ScalatestRouteTest should support" - {

    "the most simple and direct route test" in {
      Get() ~> {
        (_: RequestContext).complete(HttpResponse())
      } ~> (_.response) shouldEqual HttpResponse()
    }

    "a test using a directive and some checks" in {
      val pinkHeader = RawHeader("Fancy", "pink")
      Get() ~> addHeader(pinkHeader) ~> {
        respondWithHeader(pinkHeader) { complete("abc") }
      } ~> check {
        status shouldEqual OK
        body shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), "abc")
        header("Fancy") shouldEqual Some(pinkHeader)
      }
    }

    "proper rejection collection" in {
      Post("/abc", "content") ~> {
        (get | put) {
          complete("naah")
        }
      } ~> check {
        rejections shouldEqual List(MethodRejection(GET), MethodRejection(PUT))
      }
    }

    "separate running route from checking" in {
      val pinkHeader = RawHeader("Fancy", "pink")

      case class HandleRequest(ctx: RequestContext)
      val service = TestProbe()
      val handler = TestProbe()

      val result =
        Get() ~> addHeader(pinkHeader) ~> {
          respondWithHeader(pinkHeader) { ctx ⇒ service.send(handler.ref, HandleRequest(ctx)) }
        } ~> runRoute

      val ctx = handler.expectMsgType[HandleRequest].ctx
      ctx.complete("abc")

      check {
        status shouldEqual OK
        body shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), "abc")
        header("Fancy") shouldEqual Some(pinkHeader)
      }(result)
    }
  }

}
