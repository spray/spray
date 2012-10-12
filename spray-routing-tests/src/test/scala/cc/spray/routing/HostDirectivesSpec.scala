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

import spray.http.HttpHeaders.Host
import shapeless._


class HostDirectivesSpec extends RoutingSpec {

  "The 'host' directive" should {

    "in its simple String form" in {

      "block requests to unmatched hosts" in {
        Get() ~> Host("spray.io") ~> {
          host("spray.com") { completeOk }
        } ~> check { handled === false }
      }

      "let requests to matching hosts pass" in {
        Get() ~> Host("spray.io") ~> {
          host("spray.io") { completeOk }
        } ~> check { response === Ok }
      }
    }

    "in its simple RegEx form" in {

      "block requests to unmatched hosts" in {
        Get() ~> Host("spray.io") ~> {
          host("hairspray.*".r) { echoComplete }
        } ~> check { handled === false }
      }

      "let requests to matching hosts pass and extract the full host" in {
        Get() ~> Host("spray.io") ~> {
          host("spra.*".r) { echoComplete }
        } ~> check { entityAs[String] === "spray.io" }
      }
    }

    "in its group RegEx form" in {

      "block requests to unmatched hosts" in {
        Get() ~> Host("spray.io") ~> {
          host("hairspray(.*)".r) { echoComplete }
        } ~> check { handled === false }
      }

      "let requests to matching hosts pass and extract the full host" in {
        Get() ~> Host("spray.io") ~> {
          host("spra(.*)".r) { echoComplete }
        } ~> check { entityAs[String] === "y.io" }
      }
    }
  }

}