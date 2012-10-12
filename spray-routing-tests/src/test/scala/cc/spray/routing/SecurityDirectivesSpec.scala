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

import akka.dispatch.Promise
import authentication._
import spray.http._
import HttpHeaders._


class SecurityDirectivesSpec extends RoutingSpec {

  val dontAuth = UserPassAuthenticator[BasicUserContext](_ => Promise.successful(None))

  val doAuth = UserPassAuthenticator[BasicUserContext] { userPassOption =>
    Promise.successful(Some(BasicUserContext(userPassOption.get.user)))
  }

  "the 'authenticate(BasicAuth())' directive" should {
    "reject requests without Authorization header with an AuthenticationRequiredRejection" in {
      Get() ~> {
        authenticate(BasicAuth(dontAuth, "Realm")) { echoComplete }
      } ~> check { rejection === AuthenticationRequiredRejection("Basic", "Realm", Map.empty) }
    }
    "reject unauthenticated requests with Authorization header with an AuthorizationFailedRejection" in {
      Get() ~> addHeader(Authorization(BasicHttpCredentials("Bob", ""))) ~> {
        authenticate(BasicAuth(dontAuth, "Realm")) { echoComplete }
      } ~> check { rejection === AuthenticationFailedRejection("Realm") }
    }
    "extract the object representing the user identity created by successful authentication" in {
      Get() ~> addHeader(Authorization(BasicHttpCredentials("Alice", ""))) ~> {
        authenticate(BasicAuth(doAuth, "Realm")) { echoComplete }
      } ~> check { entityAs[String] === "BasicUserContext(Alice)" }
    }
  }
}