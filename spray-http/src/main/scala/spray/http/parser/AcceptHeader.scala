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

import scala.annotation.tailrec
import org.parboiled.scala._
import BasicRules._

private[parser] trait AcceptHeader {
  this: Parser with ProtocolParameterRules with CommonActions ⇒

  def `*Accept` = rule(
    zeroOrMore(MediaRangeDecl, separator = ListSep) ~ EOI ~~> (HttpHeaders.Accept(_)))

  def MediaRangeDecl = rule {
    MediaRangeDef ~ zeroOrMore(";" ~ Parameter) ~~> { (main, sub, params) ⇒
      // we don't support q values yet and don't want them to cause creation of custom MediaTypes every time
      // we see them, so we filter them out of the parameter list here
      @tailrec def toNonQValueMap(remaining: List[(String, String)],
                                  builder: StringMapBuilder = null): Map[String, String] =
        remaining match {
          case Nil              ⇒ if (builder eq null) Map.empty else builder.result()
          case ("q", _) :: tail ⇒ toNonQValueMap(tail, builder)
          case kvp :: tail ⇒
            val b = if (builder eq null) Map.newBuilder[String, String] else builder
            b += kvp
            toNonQValueMap(tail, b)
        }

      if (sub == "*") {
        val mainLower = main.toLowerCase
        val parameters = toNonQValueMap(params)
        if (parameters.isEmpty) MediaRanges.getForKey(mainLower) getOrElse MediaRange.custom(mainLower)
        else MediaRange.custom(mainLower, parameters)
      } else getMediaType(main, sub, parameters = toNonQValueMap(params))
    }
  }

  def MediaRangeDef = rule {
    "*/*" ~ push("*", "*") | Type ~ "/" ~ ("*" ~ push("*") | Subtype) | "*" ~ push("*", "*")
  }
}