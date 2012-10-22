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

package spray.routing

import spray.http._
import HttpHeaders._
import StatusCodes._
import MediaTypes._


class RouteDirectivesSpec extends RoutingSpec {

  "The `complete` directive" should {
    "by chainable with the `&` operator" in {
      Get() ~> (get & complete("yeah")) ~> check { entityAs[String] === "yeah" }
    }
    "allow for factoring out a StandardRoute" in {
      Get() ~> (get & complete)("yeah") ~> check { entityAs[String] === "yeah" }
    }
  }

  "the redirect directive" should {
    "produce proper 'Found' redirections" in {
      Get() ~> {
        redirect("/foo", Found)
      } ~> check {
        response === HttpResponse(
          status = 302,
          entity = HttpBody(`text/html`, "The requested resource temporarily resides under <a href=\"/foo\">this URI</a>."),
          headers = Location("/foo") :: Nil
        )
      }
    }
    "produce proper 'NotModified' redirections" in {
      Get() ~> {
        redirect("/foo", NotModified)
      } ~> check { response === HttpResponse(304, headers = Location("/foo") :: Nil) }
    }
  }
  
}