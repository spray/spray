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

package spray.routing

import akka.dispatch.Promise
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
    "be lazy in its argument evaluation, independently of application style" in {
      var i = 0
      Put() ~> {
        get { complete { i += 1; "get" } } ~
          put { complete { i += 1; "put" } } ~
          (post & complete { i += 1; "post" }) ~
          (delete & complete) { i += 1; "delete" }
      } ~> check {
        entityAs[String] === "put"
        i === 1
      }
    }
    "support completion from response futures" in {
      Get() ~> {
        get & complete(Promise.successful(HttpResponse(entity = "yup")).future)
      } ~> check { entityAs[String] === "yup" }
    }
  }

  "the redirect directive" should {
    "produce proper 'Found' redirections" in {
      Get() ~> {
        redirect("/foo", Found)
      } ~> check {
        response === HttpResponse(
          status = 302,
          entity = HttpEntity(`text/html`, "The requested resource temporarily resides under <a href=\"/foo\">this URI</a>."),
          headers = Location("/foo") :: Nil)
      }
    }
    "produce proper 'NotModified' redirections" in {
      Get() ~> {
        redirect("/foo", NotModified)
      } ~> check { response === HttpResponse(304, headers = Location("/foo") :: Nil) }
    }
  }

}
