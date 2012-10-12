/*
 * Copyright (C) 2011-2012 spray.io
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
  extension: Option[String] = None
) {
  def value: String = name + "=\"" + content + '"' +
                      expires.map("; Expires=" + _.toRfc1123DateTimeString).getOrElse("") +
                      maxAge.map("; Max-Age=" + _).getOrElse("") +
                      domain.map("; Domain=" + _).getOrElse("") +
                      path.map("; Path=" + _).getOrElse("") +
                      (if (secure) "; Secure" else "") +
                      (if (httpOnly) "; HttpOnly" else "") +
                      extension.map("; " + _).getOrElse("")

  override def toString = value
}
