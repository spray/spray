/*
 * Copyright (C) 2011-2013 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

package spray.http

import spray.http.parser.CharPredicate

// see http://tools.ietf.org/html/rfc6265
case class HttpCookie(
    name: String,
    content: String,
    expires: Option[DateTime] = None,
    maxAge: Option[Long] = None,
    domain: Option[String] = None,
    path: Option[String] = None,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    extension: Option[String] = None) extends ValueRenderable {

  import HttpCookie._

  // TODO: suppress running these requires for cookies created from our header parser
  require(nameChars.matchAll(name), "'" + nameChars.firstMismatch(name).get + "' not allowed in cookie name ('" + name + "')")
  require(contentChars.matchAll(content), "'" + contentChars.firstMismatch(content).get + "' not allowed in cookie content ('$content')")
  require(domain.isEmpty || domainChars.matchAll(domain.get), "'" + domainChars.firstMismatch(domain.get).get + "' not allowed in cookie domain ('" + domain.get + "')")
  require(path.isEmpty || pathOrExtChars.matchAll(path.get), "'" + pathOrExtChars.firstMismatch(path.get).get + "' not allowed in cookie path ('" + path.get + "')")
  require(extension.isEmpty || pathOrExtChars.matchAll(extension.get), "'" + pathOrExtChars.firstMismatch(extension.get).get + "' not allowed in cookie extension ('" + extension.get + "')")

  def render[R <: Rendering](r: R): R = {
    r ~~ name ~~ '=' ~~ content
    if (expires.isDefined) expires.get.renderRfc1123DateTimeString(r ~~ "; Expires=")
    if (maxAge.isDefined) r ~~ "; Max-Age=" ~~ maxAge.get
    if (domain.isDefined) r ~~ "; Domain=" ~~ domain.get
    if (path.isDefined) r ~~ "; Path=" ~~ path.get
    if (secure) r ~~ "; Secure"
    if (httpOnly) r ~~ "; HttpOnly"
    if (extension.isDefined) r ~~ ';' ~~ ' ' ~~ extension.get
    r
  }
}

object HttpCookie {
  import CharPredicate._

  def nameChars = HttpToken
  // http://tools.ietf.org/html/rfc6265#section-4.1.1
  // ; US-ASCII characters excluding CTLs, whitespace DQUOTE, comma, semicolon, and backslash
  val contentChars = CharPredicate('\u0021', '\u0023' to '\u002B', '\u002D' to '\u003A', '\u003C' to '\u005B', '\u005D' to '\u007E')
  val domainChars = AlphaNum ++ ".-"
  val pathOrExtChars = Visible -- ';'
}
