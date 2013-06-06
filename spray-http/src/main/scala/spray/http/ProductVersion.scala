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

import spray.http.parser.HttpParser

case class ProductVersion(product: String = "", version: String = "", comment: String = "") extends ValueRenderable {
  def render[R <: Rendering](r: R): R = {
    r ~~ product
    if (!version.isEmpty) r ~~ '/' ~~ version
    if (!comment.isEmpty) {
      if (!product.isEmpty || !version.isEmpty) r ~~ ' '
      r ~~ '(' ~~ comment ~~ ')'
    }
    r
  }
}

object ProductVersion {
  def parseMultiple(string: String): Seq[ProductVersion] =
    parser.HttpParser.parse(HttpParser.ProductVersionComments, string) match {
      case Right(x)   ⇒ x
      case Left(info) ⇒ throw new IllegalArgumentException("'" + string + "' is not a legal sequence of ProductVersions: " + info.formatPretty)
    }
}
