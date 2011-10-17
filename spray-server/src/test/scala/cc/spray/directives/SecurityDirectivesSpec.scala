/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package directives

import http._
import HttpHeaders._
import test.AbstractSprayTest

class SecurityDirectivesSpec extends AbstractSprayTest {

  val dontAuth = new UserPassAuthenticator[BasicUserContext] {
    def apply(userPass: Option[(String, String)]) = None
  }
  
  val doAuth = new UserPassAuthenticator[BasicUserContext] {
    def apply(userPass: Option[(String, String)]) = Some(BasicUserContext(userPass.get._1))
  }
  
  "the 'authenticate(HttpBasic())' directive" should {
    "reject requests without Authorization header with an AuthenticationRequiredRejection" in {
      test(HttpRequest()) { 
        authenticate(httpBasic(authenticator = dontAuth)) { _ => completeOk }
      }.rejections mustEqual Set(AuthenticationRequiredRejection("Basic", "Secured Resource", Map.empty))
    }
    "reject unauthenticated requests with Authorization header with an AuthorizationFailedRejection" in {
      test(HttpRequest(headers = List(Authorization(BasicHttpCredentials("Bob", ""))))) { 
        authenticate(httpBasic(authenticator = dontAuth)) { _ => completeOk }
      }.rejections mustEqual Set(AuthenticationFailedRejection("Secured Resource"))
    }
    "extract the object representing the user identity created by successful authentication" in {
      test(HttpRequest(headers = List(Authorization(BasicHttpCredentials("Alice", ""))))) { 
        authenticate(httpBasic(authenticator = doAuth)) { echoComplete }
      }.response.content.as[String] mustEqual Right("BasicUserContext(Alice)")
    }
  }

  "the FromConfigUserPassAuthenticator" should {
    "extract a BasicUserContext for users defined in the spray config" in {
      test(HttpRequest(headers = List(Authorization(BasicHttpCredentials("Alice", "banana"))))) {
        authenticate(httpBasic()) { echoComplete }
      }.response.content.as[String] mustEqual Right("BasicUserContext(Alice)")
    }
  }
}