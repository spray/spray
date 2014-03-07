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
import spray.http._

private[parser] trait RangeHeaders {
  this: Parser with ProtocolParameterRules ⇒
  import BasicRules._

  // http://tools.ietf.org/html/rfc2616#section-14.5

  def `*Accept-Ranges` = rule {
    ("none" ~ push(Nil) | oneOrMore(`range-unit`, separator = ListSep)) ~ EOI ~~> (HttpHeaders.`Accept-Ranges`(_))
  }

  // http://tools.ietf.org/html/rfc2616#section-14.16

  def `*Content-Range` = rule { `content-range-spec` ~ EOI ~~> (HttpHeaders.`Content-Range`(_, _)) }

  def `content-range-spec` = `byte-content-range-spec`

  def `byte-content-range-spec` = rule { `bytes-unit` ~ SP ~ `byte-range-resp-spec` }

  def `byte-range-resp-spec` = rule(
    `first-byte-pos` ~ ch('-') ~ `last-byte-pos` ~ InstanceLength ~~> ContentRange.Default
      | ch('*') ~ InstanceLength ~~> (ContentRange.Unsatisfiable(_)))

  def InstanceLength = rule { ch('/') ~ (longExpression ~~> (Some(_)) | ch('*') ~ push(None)) }

  // http://tools.ietf.org/html/rfc2616#section-14.27

  def `*If-Range` = rule { (EntityTag ~~> (Left(_)) | HttpDate ~~> (Right(_))) ~ EOI ~~> (HttpHeaders.`If-Range`(_)) }

  // http://tools.ietf.org/html/rfc2616#section-14.35.1

  def `*Range` = rule(`ranges-specifier` ~ EOI ~~> (HttpHeaders.Range(_, _)))

  def `ranges-specifier` = `byte-ranges-specifier` // | `other-ranges-specifier` // not supported

  def `byte-ranges-specifier` = rule { `bytes-unit` ~ ch('=') ~ `byte-range-set` }

  def `byte-range-set` = rule { oneOrMore(`byte-range-spec` | `suffix-byte-range-spec`, separator = ListSep) }

  def `byte-range-spec` = rule {
    `first-byte-pos` ~ ch('-') ~ (`last-byte-pos` ~~> (ByteRange.Slice(_: Long, _)) | EMPTY ~~> (ByteRange.FromOffset(_: Long)))
  }

  def `first-byte-pos` = longExpression

  def `last-byte-pos` = longExpression

  def `suffix-byte-range-spec` = rule { ch('-') ~ `suffix-length` ~~> ByteRange.Suffix }

  def `suffix-length` = longExpression

  def longExpression = rule { oneOrMore(Digit) ~> (_.toLong) }
}