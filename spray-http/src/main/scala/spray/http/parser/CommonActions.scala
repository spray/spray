/*
 * Copyright (C) 2011-2013 spray.io
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
package parser

import spray.http.MediaTypes._
import org.parboiled.errors.ParsingException

private[parser] trait CommonActions {

  type StringMapBuilder = scala.collection.mutable.Builder[(String, String), Map[String, String]]

  def getMediaType(mainType: String, subType: String, boundary: String = "",
                   parameters: Map[String, String] = Map.empty): MediaType = {
    mainType.toLowerCase match {
      case "multipart" ⇒ subType.toLowerCase match {
        case "mixed"       ⇒ new `multipart/mixed`(boundary, parameters)
        case "alternative" ⇒ new `multipart/alternative`(boundary, parameters)
        case "related"     ⇒ new `multipart/related`(boundary, parameters)
        case "form-data"   ⇒ new `multipart/form-data`(boundary, parameters)
        case "signed"      ⇒ new `multipart/signed`(boundary, parameters)
        case "encrypted"   ⇒ new `multipart/encrypted`(boundary, parameters)
        case custom        ⇒ new MultipartMediaType(custom, boundary, parameters)
      }
      case mainLower ⇒
        val registered = if (parameters.isEmpty) MediaTypes.getForKey((mainLower, subType.toLowerCase)) else None
        registered getOrElse MediaType.custom(mainType, subType, parameters = parameters)
    }
  }

  val getCharset: String ⇒ HttpCharset = { charsetName ⇒
    HttpCharsets
      .getForKey(charsetName.toLowerCase)
      .orElse(HttpCharset.custom(charsetName))
      .getOrElse(throw new ParsingException("Unsupported charset: " + charsetName))
  }
}