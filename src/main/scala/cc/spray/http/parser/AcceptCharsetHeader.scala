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

package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._
import Charsets._

private[parser] trait AcceptCharsetHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_CHARSET = rule (
    oneOrMore(CharsetRangeDecl, ListSep) ~ EOI
            ~~> (x => HttpHeaders.`Accept-Charset`(x))
  )
  
  def CharsetRangeDecl = rule (
    CharsetRangeDef ~ optional(CharsetQuality) 
  )
  
  def CharsetRangeDef = rule (
      "*" ~ push(`*`)
    | Charset ~~> (x => Charsets.getForKey(x.toLowerCase).getOrElse(CustomCharset(x)))  
  )
  
  def CharsetQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support charset quality
  }
  
}