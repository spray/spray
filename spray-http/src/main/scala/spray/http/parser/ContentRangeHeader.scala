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

import org.parboiled.scala._
import spray.http.parser.BasicRules._

/**
 * content-range-spec      = byte-content-range-spec
 * byte-content-range-spec = bytes-unit SP byte-range-resp-spec "/" ( instance-length | "*" )
 * byte-range-resp-spec    = (first-byte-pos "-" last-byte-pos) | "*"
 * instance-length         = 1*DIGIT
 */
private[parser] trait ContentRangeHeader {
  this: Parser with ProtocolParameterRules with RangeHeader ⇒

  def `*Content-Range` = rule(ContentRangeSpec) ~ EOI ~~> ((range, length) ⇒ { HttpHeaders.`Content-Range`(ContentRange(range.map(_._1), range.map(_._2), length)) })
  def ContentRangeSpec = rule { ByteContentRangeSpec }
  def ByteContentRangeSpec = rule { BytesUnit ~ DROP ~ SP ~ BytesRangeResponseSpec ~ ch('/') ~ ((InstanceLength ~~> (a ⇒ Some(a))) | ch('*') ~> (_ ⇒ None)) }
  def BytesRangeResponseSpec = rule { (FirstBytePosition ~ ch('-') ~ LastBytePosition) ~~> ((a, b) ⇒ Some(a, b)) | ch('*') ~> (_ ⇒ None) }
  def InstanceLength = rule { oneOrMore(Digit) ~> (_.toLong) }

}