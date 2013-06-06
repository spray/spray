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

package spray
package http

import HttpCharsets._
import org.parboiled.common.Base64
import scala.annotation.tailrec

sealed trait HttpCredentials extends ValueRenderable

case class BasicHttpCredentials(username: String, password: String) extends HttpCredentials {
  def render[R <: Rendering](r: R): R = {
    val userPass = username + ':' + password
    val bytes = userPass.getBytes(`ISO-8859-1`.nioCharset)
    val cookie = Base64.rfc2045.encodeToChar(bytes, false)
    r ~~ "Basic " ~~ cookie
  }
}

object BasicHttpCredentials {
  def apply(credentials: String): BasicHttpCredentials = {
    val bytes = Base64.rfc2045.decodeFast(credentials)
    val userPass = new String(bytes, `ISO-8859-1`.nioCharset)
    userPass.indexOf(':') match {
      case -1 ⇒ apply(userPass, "")
      case ix ⇒ apply(userPass.substring(0, ix), userPass.substring(ix + 1))
    }
  }
}

case class OAuth2BearerToken(token: String) extends HttpCredentials {
  def render[R <: Rendering](r: R): R = r ~~ "Bearer " ~~ token
}

case class GenericHttpCredentials(scheme: String, token: String,
                                  params: Map[String, String] = Map.empty) extends HttpCredentials {

  def render[R <: Rendering](r: R): R = {
    r ~~ scheme
    if (!token.isEmpty) r ~~ ' ' ~~ token
    if (params.nonEmpty)
      params foreach new (((String, String)) ⇒ Unit) {
        var first = true
        def apply(kvp: (String, String)): Unit = {
          val (k, v) = kvp
          if (first) { r ~~ ' '; first = false } else r ~~ ','
          if (!k.isEmpty) r ~~ k ~~ '='
          r ~~# v
        }
      }
    r
  }
}

object GenericHttpCredentials {
  def apply(scheme: String, params: Map[String, String]): GenericHttpCredentials = apply(scheme, "", params)
}
