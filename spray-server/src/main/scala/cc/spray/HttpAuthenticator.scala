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
        else AuthorizationFailedRejection 
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
trait BasicHttpAuthenticator[U] extends HttpAuthenticator[U] {

  def scheme = "Basic"

  def params(ctx: RequestContext) = Map.empty

  def authenticate(credentials: Option[HttpCredentials], ctx: RequestContext) = {
    authenticate {
      credentials.flatMap {
        case BasicHttpCredentials(user, pass) => Some(user -> pass)
        case _ => None
      }
    }
  }

  def authenticate(userPass: Option[(String, String)]): Option[U]
}
  
object HttpBasic {

  /**
   * Creates an authenticator for Http Basic Auth using the in-scope implicit [[cc.spray.UserPassAuthenticator]].
   */
  def apply[U](authRealm: String = "Secured Resource")
              (implicit authenticator: UserPassAuthenticator[U]): BasicHttpAuthenticator[U] = {
    new BasicHttpAuthenticator[U] {
      def realm = authRealm
      def authenticate(userPass: Option[(String, String)]) = authenticator(userPass)
    }
  }
}

/**
 * A UserPassAuthenticator can authenticate users via a username/password combination and provide a corresponding
 * user object.
 */
trait UserPassAuthenticator[U] extends (Option[(String, String)] => Option[U])

case class BasicUserContext(username: String)