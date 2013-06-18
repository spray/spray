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
import spray.http._
import HttpMethods._
import HttpHeaders._

class RequestBuildingSpec extends Specification with RequestBuilding {

  "The RequestBuilding trait" should {
    "construct simple requests" >> {
      Get() === HttpRequest()
      Options() === HttpRequest(OPTIONS)
      Head() === HttpRequest(HEAD)
      Post("/abc") === HttpRequest(POST, "/abc")
      Patch("/abc", "content") === HttpRequest(PATCH, "/abc", entity = "content")
      Put("/abc", Some("content")) === HttpRequest(PUT, "/abc", entity = "content")
    }

    "provide a working `addHeader` transformer" >> {
      Get() ~> addHeader("X-Yeah", "Naah") ~> addHeader(Authorization(BasicHttpCredentials("bla"))) ===
        HttpRequest(headers = List(Authorization(BasicHttpCredentials("bla")), RawHeader("X-Yeah", "Naah")))
    }

    "support adding headers directly without explicit `addHeader` transformer" >> {
      Get() ~> RawHeader("X-Yeah", "Naah") === HttpRequest(headers = List(RawHeader("X-Yeah", "Naah")))
    }

    "provide the ability to add generic Authorization credentials to the request" >> {
      val creds = GenericHttpCredentials("OAuth", Map("oauth_version" -> "1.0"))
      Get() ~> addCredentials(creds) === HttpRequest(headers = List(Authorization(creds)))
    }
  }

}
