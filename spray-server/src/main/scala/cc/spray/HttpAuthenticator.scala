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

import http._
import HttpHeaders._

/**
 * An HttpAuthenticator is a GeneralAuthenticator that uses credentials passed to the server via the
 * HTTP `Authorization` header to authenticate the user and extract a user object.
 */
trait HttpAuthenticator[U] extends GeneralAuthenticator[U] {

  def apply(ctx: RequestContext) = {
    val authHeader = ctx.request.headers.findByType[`Authorization`]
    val credentials = authHeader.map { case Authorization(credentials) => credentials }
    authenticate(credentials, ctx) match {
      case Some(userContext) => Right(userContext)
      case None => Left {
        if (authHeader.isEmpty) AuthenticationRequiredRejection(scheme, realm, params(ctx))
        else AuthenticationFailedRejection(realm)
      }
    }
  }

  def scheme: String

  def realm: String
  
  def params(ctx: RequestContext): Map[String, String]
  
  def authenticate(credentials: Option[HttpCredentials], ctx: RequestContext): Option[U]
}

/**
 * The BasicHttpAuthenticator implements HTTP Basic Auth.
 */
class BasicHttpAuthenticator[U](val realm: String, val authenticator: UserPassAuthenticator[U])
        extends HttpAuthenticator[U] {

  def scheme = "Basic"

  def params(ctx: RequestContext) = Map.empty

  def authenticate(credentials: Option[HttpCredentials], ctx: RequestContext) = {
    authenticator {
      credentials.flatMap {
        case BasicHttpCredentials(user, pass) => Some(user -> pass)
        case _ => None
      }
    }
  }
}
  
/**
 * A UserPassAuthenticator that uses plain-text username/password definitions from the spray/akka config file
 * for authentication. The config section should look like this:
 * {{{
 * spray {
 *   .... # other spray settings
 *   users {
 *     username = "password"
 *     ...
 *   }
 * ...
 * }
 * }}}
 */
object FromConfigUserPassAuthenticator extends UserPassAuthenticator[BasicUserContext] {
  def apply(userPass: Option[(String, String)]) = userPass.flatMap {
    case (user, pass) => {
      akka.config.Config.config.getString("spray.users." + user).flatMap { pw =>
        if (pw == pass) {
          Some(BasicUserContext(user))
        } else {
          None
        }
      }
    }
  }
}

case class BasicUserContext(username: String)