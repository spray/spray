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

import authentication.{BasicHttpAuthenticator, FromConfigUserPassAuthenticator}

private[spray] trait SecurityDirectives {
  this: BasicDirectives =>

  /**
   * Wraps its inner Route with authentication support.
   * Uses the given authenticator to authenticate the user and extract an object representing the users identity.
   * It's up to the given authenticator how to deal with authentication failures of any kind.
   */
  def authenticate[U](authenticator: GeneralAuthenticator[U]) = filter1 { ctx =>
    authenticator(ctx) match {
      case Right(userContext) => Pass(userContext)
      case Left(rejection) => Reject(rejection)
    }
  }

  /**
   * Applies the given authorization check to the request.
   * If the check fails the route is rejected with an [[cc.spray.AuthorizationFailedRejection]].
   */
  def authorize(check: => Boolean): SprayRoute0 = authorize(_ => check)
  
  /**
   * Applies the given authorization check to the request.
   * If the check fails the route is rejected with an [[cc.spray.AuthorizationFailedRejection]].
   */
  def authorize(check: RequestContext => Boolean): SprayRoute0 = filter { ctx =>
    if (check(ctx)) Pass else Reject(AuthorizationFailedRejection)
  }

  /**
   * Convenience method for the creation of a BasicHttpAuthenticator instance.
   */
  def httpBasic[U](realm: String = "Secured Resource",
                   authenticator: UserPassAuthenticator[U] = FromConfigUserPassAuthenticator): BasicHttpAuthenticator[U] =
      new BasicHttpAuthenticator[U](realm, authenticator)
}