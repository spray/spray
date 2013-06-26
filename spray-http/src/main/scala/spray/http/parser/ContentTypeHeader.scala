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

package spray
package http
package parser

import scala.annotation.tailrec
import org.parboiled.scala._
import HttpHeaders._
import ProtectedHeaderCreation.enable

private[parser] trait ContentTypeHeader {
  this: Parser with ProtocolParameterRules with CommonActions ⇒

  def `*Content-Type` = rule {
    ContentTypeHeaderValue ~~> (`Content-Type`(_))
  }

  lazy val ContentTypeHeaderValue = rule {
    MediaTypeDef ~ EOI ~~> { (main, sub, params) ⇒
      @tailrec def processParams(remaining: List[(String, String)] = params,
                                 boundary: String = "",
                                 charset: Option[HttpCharset] = None,
                                 builder: StringMapBuilder = null): (String, Option[HttpCharset], Map[String, String]) =
        remaining match {
          case Nil                         ⇒ (boundary, charset, if (builder eq null) Map.empty else builder.result())
          case ("boundary", value) :: tail ⇒ processParams(tail, value, charset, builder)
          case ("charset", value) :: tail  ⇒ processParams(tail, boundary, Some(getCharset(value)), builder)
          case kvp :: tail ⇒
            val b = if (builder eq null) Map.newBuilder[String, String] else builder
            b += kvp
            processParams(tail, boundary, charset, b)
        }

      val (boundary, charset, parameters) = processParams()
      val mediaType = getMediaType(main, sub, boundary, parameters)
      ContentType(mediaType, charset)
    }
  }
}
