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

package spray.http
package parser

import spray.http.MediaTypes._
import org.parboiled.errors.ParsingException

private[parser] trait CommonActions {
  
  def getMediaType(mainType: String, subType: String, boundary: Option[String] = None): MediaType = {
    mainType.toLowerCase match {
      case "multipart" => subType.toLowerCase match {
        case "mixed"       => new `multipart/mixed`      (boundary)
        case "alternative" => new `multipart/alternative`(boundary)
        case "related"     => new `multipart/related`    (boundary)
        case "form-data"   => new `multipart/form-data`  (boundary)
        case "signed"      => new `multipart/signed`     (boundary)
        case "encrypted"   => new `multipart/encrypted`  (boundary)
        case custom        => new MultipartMediaType(custom, boundary)
      }
      case mainLower =>
        MediaTypes.getForKey((mainLower, subType.toLowerCase)).getOrElse(new CustomMediaType(mainType, subType))
    }
  }

  val getCharset: String => HttpCharset = { charsetName =>
    HttpCharsets
      .getForKey(charsetName.toLowerCase)
      .orElse(HttpCharsets.CustomHttpCharset(charsetName))
      .getOrElse(throw new ParsingException("Unsupported charset: " + charsetName))
  }
}