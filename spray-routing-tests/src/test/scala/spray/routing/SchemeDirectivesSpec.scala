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

class SchemeDirectivesSpec extends RoutingSpec {
  "the schemeName directive" should {
    "extract the Uri scheme" in {
      Put("http://localhost/", "Hello") ~> schemeName { echoComplete } ~> check { entityAs[String] === "http" }
    }
  }

  """the scheme("http") directive""" should {
    "let requests with an http Uri scheme pass" in {
      Put("http://localhost/", "Hello") ~> scheme("http") { completeOk } ~> check { response === Ok }
    }
    "reject requests with an https Uri scheme" in {
      Get("https://localhost/") ~> scheme("http") { completeOk } ~> check { rejection === SchemeRejection("http") }
    }
  }

  """the scheme("https") directive""" should {
    "let requests with an https Uri scheme pass" in {
      Put("https://localhost/", "Hello") ~> scheme("https") { completeOk } ~> check { response === Ok }
    }
    "reject requests with an http Uri scheme" in {
      Get("http://localhost/") ~> scheme("https") { completeOk } ~> check { rejection === SchemeRejection("https") }
    }
  }
}
