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


private[spray] trait CookieDirectives {
  this: BasicDirectives with MiscDirectives =>

  /**
   * Returns a route filter that extracts an HttpCookie with the given name.
   * If the cookie is not present the request is rejected with a respective [[cc.spray.MissingCookieRejection]].
   */
  def cookie(name: String): SprayRoute1[HttpCookie] = {
    val directive = headerValue {
      case HttpHeaders.Cookie(cookies) => cookies.find(_.name == name)
      case _ => None
    }
    filter1 {
      directive.filter(_).mapRejections(_ => MissingCookieRejection(name))
    }
  }

  /**
   * Returns a route filter that extracts an HttpCookie with the given name.
   * If the cookie is not present a value of `None` is extracted.
   */
  def optionalCookie(name: String): SprayRoute1[Option[HttpCookie]] = {
    cookie(name).map(Some(_) :Option[HttpCookie]) | provide(None)
  }

  /**
   * A directive that adds a Set-Cookie header with the given cookie to all responses of its inner route.
   */
  def setCookie(cookie: HttpCookie) =
    respondWithHeader(`Set-Cookie`(cookie))

  /**
   * A directive thats adds a Set-Cookie header expiring the given cookie to all responses of its inner route.
   */
  def deleteCookie(cookie: HttpCookie): SprayRoute0 =
    respondWithHeader(`Set-Cookie`(cookie.copy(content = "deleted", expires = Some(DateTime.MinValue))))

  /**
   * A directive thats adds a Set-Cookie header expiring the given cookie to all responses of its inner route.
   */
  def deleteCookie(name: String, domain: String = "", path: String = ""): SprayRoute0 = {
    import util._
    deleteCookie(HttpCookie(name, "", domain = domain.toOption, path = path.toOption))
  }

}
