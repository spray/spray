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

import org.specs2.mutable.Specification
import spray.routing.{MethodRejection, RequestContext, Directives}
import spray.http._
import HttpMethods._
import MediaTypes._
import HttpCharsets._


class Specs2RouteTestSpec extends Specification with Directives with Specs2RouteTest {

  "The routing infrastructure should support" >> {

    "the most simple and direct route" in {
      Get() ~> complete(HttpResponse()) ~> (_.response) === HttpResponse()
    }

    "a basic directive" in {
      Get() ~> complete("abc") ~> check {
        body === HttpBody(ContentType(`text/plain`, `ISO-8859-1`), "abc")
      }
    }

    "proper rejection collection" in {
      Post("/abc", "content") ~> {
        (get | put) { complete("naah") }
      } ~> check {
        rejections === List(MethodRejection(GET), MethodRejection(PUT))
      }
    }
  }
  
}