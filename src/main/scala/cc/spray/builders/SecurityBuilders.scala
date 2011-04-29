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
package builders

private[spray] trait SecurityBuilders extends DefaultUserPassAuthenticator {
  this: FilterBuilders =>

  /**
   * Uses the given authenticator to authenticate the user and extract an object representing the users identity.
   * It's up to the given authenticator how to deal with authentication failures of any kind.
   */
  def authenticate[U](authenticator: GeneralAuthenticator[U]) = filter1(authenticator)

  /**
   * Applies the given authorization check to the request.
   * If the check fails the route is rejected with an [[cc.spray.AuthorizationFailedRejection]].
   */
  def authorize(check: => Boolean): FilterRoute0 = authorize(_ => check)
  
  /**
   * Applies the given authorization check to the request.
   * If the check fails the route is rejected with an [[cc.spray.AuthorizationFailedRejection]].
   */
  def authorize(check: RequestContext => Boolean): FilterRoute0 = filter { ctx =>
    if (check(ctx)) Pass() else Reject(AuthorizationFailedRejection)
  }
}

// introduces one more layer in the inheritance chain in order to lower the priority of the contained implicits
private[spray] trait DefaultUserPassAuthenticator {
  
  implicit object FromConfigUserPassAuthenticator extends UserPassAuthenticator[BasicUserContext] {
    def apply(userPass: Option[(String, String)]) = userPass.flatMap {
      case (user, pass) => {
        akka.config.Config.config.getString("spray.users." + user).flatMap { pw =>
          if (pw == pass) Some(BasicUserContext(user))
          else None
        }
      }
    }
  }
  
}