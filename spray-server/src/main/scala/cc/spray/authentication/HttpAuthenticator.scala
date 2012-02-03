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
package authentication

import http._
import utils._
import HttpHeaders._
import akka.dispatch.Future

/**
 * An HttpAuthenticator is a GeneralAuthenticator that uses credentials passed to the server via the
 * HTTP `Authorization` header to authenticate the user and extract a user object.
 */
trait HttpAuthenticator[U] extends GeneralAuthenticator[U] {

  def apply(ctx: RequestContext) = {
    val authHeader = ctx.request.headers.findByType[`Authorization`]
    val credentials = authHeader.map { case Authorization(creds) => creds }
    authenticate(credentials, ctx) map {
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
  
  def authenticate(credentials: Option[HttpCredentials], ctx: RequestContext): Future[Option[U]]
}