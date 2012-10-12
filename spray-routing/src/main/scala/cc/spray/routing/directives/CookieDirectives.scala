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
package directives

import shapeless._
import spray.util._
import spray.http._
import HttpHeaders._


trait CookieDirectives {
  import BasicDirectives._
  import MiscDirectives._
  import RouteDirectives._
  import RespondWithDirectives._

  /**
   * Extracts an HttpCookie with the given name. If the cookie is not present the
   * request is rejected with a respective [[spray.routing.MissingCookieRejection]].
   */
  def cookie(name: String): Directive[HttpCookie :: HNil] =
    headerValue {
      case Cookie(cookies) => cookies.find(_.name == name)
      case _ => None
    } | reject(MissingCookieRejection(name))

  /**
   * Extracts an HttpCookie with the given name.
   * If the cookie is not present a value of `None` is extracted.
   */
  def optionalCookie(name: String): Directive[Option[HttpCookie] :: HNil] =
    cookie(name).map(_.map(shapeless.option)) | provide(None)

  /**
   * Adds a Set-Cookie header with the given cookie to all responses of its inner route.
   */
  def setCookie(cookie: HttpCookie): Directive0 = respondWithHeader(`Set-Cookie`(cookie))

  /**
   * Adds a Set-Cookie header expiring the given cookie to all responses of its inner route.
   */
  def deleteCookie(cookie: HttpCookie): Directive0 =
    respondWithHeader(`Set-Cookie`(cookie.copy(content = "deleted", expires = Some(DateTime.MinValue))))

  /**
   * Adds a Set-Cookie header expiring the given cookie to all responses of its inner route.
   */
  def deleteCookie(name: String, domain: String = "", path: String = ""): Directive0 =
    deleteCookie(HttpCookie(name, "", domain = domain.toOption, path = path.toOption))

}

object CookieDirectives extends CookieDirectives