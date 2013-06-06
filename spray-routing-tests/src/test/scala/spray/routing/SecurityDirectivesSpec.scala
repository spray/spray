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

import akka.dispatch.{ Promise, Future }
import spray.routing.authentication._
import spray.http._
import HttpHeaders._

class SecurityDirectivesSpec extends RoutingSpec {

  val dontAuth = UserPassAuthenticator[BasicUserContext](_ ⇒ Promise.successful(None))

  val doAuth = UserPassAuthenticator[BasicUserContext] { userPassOption ⇒
    Promise.successful(Some(BasicUserContext(userPassOption.get.user)))
  }

  "the 'authenticate(BasicAuth())' directive" should {
    "reject requests without Authorization header with an AuthenticationRequiredRejection" in {
      Get() ~> {
        authenticate(BasicAuth(dontAuth, "Realm")) { echoComplete }
      } ~> check { rejection === AuthenticationRequiredRejection("Basic", "Realm", Map.empty) }
    }
    "reject unauthenticated requests with Authorization header with an AuthorizationFailedRejection" in {
      Get() ~> Authorization(BasicHttpCredentials("Bob", "")) ~> {
        authenticate(BasicAuth(dontAuth, "Realm")) { echoComplete }
      } ~> check { rejection === AuthenticationFailedRejection("Realm") }
    }
    "extract the object representing the user identity created by successful authentication" in {
      Get() ~> Authorization(BasicHttpCredentials("Alice", "")) ~> {
        authenticate(BasicAuth(doAuth, "Realm")) { echoComplete }
      } ~> check { entityAs[String] === "BasicUserContext(Alice)" }
    }
    "properly handle exceptions thrown in its inner route" in {
      object TestException extends spray.util.SingletonException
      Get() ~> Authorization(BasicHttpCredentials("Alice", "")) ~> {
        handleExceptions(ExceptionHandler.default) {
          authenticate(BasicAuth(doAuth, "Realm")) { _ ⇒ throw TestException }
        }
      } ~> check { status === StatusCodes.InternalServerError }
    }
  }

  "the 'authenticate(<ContextAuthenticator>)' directive" should {
    val myAuthenticator: ContextAuthenticator[Int] = ctx ⇒ Future {
      Either.cond(ctx.request.uri.authority.host == Uri.NamedHost("spray.io"), 42,
        AuthenticationRequiredRejection("my-scheme", "MyRealm", Map()))
    }
    "reject requests not satisfying the filter condition" in {
      Get() ~> authenticate(myAuthenticator) { echoComplete } ~>
        check { rejection === AuthenticationRequiredRejection("my-scheme", "MyRealm", Map.empty) }
    }
    "pass on the authenticator extraction if the filter conditions is met" in {
      Get() ~> Host("spray.io") ~> authenticate(myAuthenticator) { echoComplete } ~>
        check { entityAs[String] === "42" }
    }
  }
}
