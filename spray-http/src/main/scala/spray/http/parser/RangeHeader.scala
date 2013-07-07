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

package spray.http.parser

import org.parboiled.scala._
import spray.http.parser.BasicRules._
import spray.http.{ HttpHeaders, SuffixByteRange, ByteRange }

/**
 * From RFC 2616  14.35.1 Byte Ranges
 *
 * ranges-specifier = byte-ranges-specifier
 * byte-ranges-specifier = bytes-unit "=" byte-range-set
 * byte-range-set  = 1#( byte-range-spec | suffix-byte-range-spec )
 * byte-range-spec = first-byte-pos "-" [last-byte-pos]
 * first-byte-pos  = 1*DIGIT
 * last-byte-pos   = 1*DIGIT
 * suffix-byte-range-spec = "-" suffix-length
 * suffix-length = 1*DIGIT
 */
private[parser] trait RangeHeader {
  this: Parser with ProtocolParameterRules ⇒

  def `*Range` = rule(RangesSpecifier) ~ EOI ~~> ((ranges) ⇒ {
    HttpHeaders.Range(ranges)
  })

  def RangesSpecifier = rule { ByteRangesSpecifier }
  def ByteRangesSpecifier = rule { BytesUnit ~ DROP ~ ch('=') ~ ByteRangeSet }
  def ByteRangeSet = rule { oneOrMore(ByteRangeSpec | SuffixByteRangeSpec, separator = ListSep) }
  def ByteRangeSpec = rule { FirstBytePosition ~ ch('-') ~ optional(LastBytePosition) ~~> ((a, b) ⇒ ByteRange(a, b)) }
  def FirstBytePosition = rule { oneOrMore(Digit) ~> (_.toLong) }
  def LastBytePosition = rule { oneOrMore(Digit) ~> (_.toLong) }
  def SuffixByteRangeSpec = rule { ch('-') ~ SuffixLength ~~> (a ⇒ SuffixByteRange(a)) }
  def SuffixLength = rule { oneOrMore(Digit) ~> (_.toLong) }
}