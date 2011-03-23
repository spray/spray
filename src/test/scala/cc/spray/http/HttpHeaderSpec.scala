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

import org.specs.Specification
import HttpHeaders._

class HttpHeaderSpec extends Specification {

  "Header 'Accept'" should {
    import MediaTypes._
    "be parsed correctly from example 1" in (
      HttpHeader("Accept", "audio/mp4; q=0.2, audio/basic") mustEqual Accept(`audio/mp4`, `audio/basic`)
    )
    "be parsed correctly from example 2" in (
      HttpHeader("Accept", "text/plain; q=0.5, text/html,\r\n text/css; q=0.8") mustEqual
              Accept(`text/plain`, `text/html`, `text/css`)
    )
  }
  
  "Header 'Accept-Charset'" should {
    import Charsets._
    "be parsed correctly from example 1" in (
      HttpHeader("Accept-Charset", "iso-8859-5, UTF-16LE;q =0.8") mustEqual `Accept-Charset`(`ISO-8859-5`, `UTF-16LE`)
    )
    "be parsed correctly from example 2" in (
      HttpHeader("Accept-Charset", "*") mustEqual `Accept-Charset`(`*`)
    )
    "be parsed correctly from example 3" in (
      HttpHeader("Accept-Charset", "pipapo; q= 1.0, utf-8") mustEqual `Accept-Charset`(CustomCharset("pipapo"), `UTF-8`)
    )
  }
  
  "Header 'Accept-Encoding'" should {
    import Encodings._
    "be parsed correctly from example 1" in (
      HttpHeader("Accept-Encoding", "compress, gzip, fancy") mustEqual `Accept-Encoding`(compress, gzip, CustomEncoding("fancy"))
    )
    "be parsed correctly from example 2" in (
      HttpHeader("Accept-Encoding", "gzip;q=1.0, identity; q=0.5, *;q=0") mustEqual `Accept-Encoding`(gzip, identity, `*`)
    )
  }
  
  "Header 'Accept-Language'" should {
    import LanguageRanges._
    "be parsed correctly from example 1" in (
      HttpHeader("Accept-Language", "da, en-gb ;q=0.8, en;q=0.7") mustEqual
              `Accept-Language`(Language("da"), Language("en", "gb"), Language("en"))
    )
    "be parsed correctly from example 2" in (
      HttpHeader("Accept-Language", "de-at-zz, *;q=0") mustEqual
              `Accept-Language`(Language("de", "at", "zz"), `*`)
    )
  }
  
  "Header 'Accept-Ranges'" should {
    import RangeUnits._
    "be parsed correctly from example 1" in (
      HttpHeader("Accept-Ranges", "bytes") mustEqual `Accept-Ranges`(bytes)
    )
    "be parsed correctly from example 2" in (
      HttpHeader("Accept-Ranges", "none") mustEqual `Accept-Ranges`(Nil)
    )
    "be parsed correctly from example 2" in (
      HttpHeader("Accept-Ranges", "bytes, fancy") mustEqual `Accept-Ranges`(bytes, CustomRangeUnit("fancy"))
    )
  }
  
  "Header 'Content-Type'" should {
    import MediaTypes._
    import Charsets._
    "be parsed correctly from example 1" in (
      HttpHeader("Content-Type", "application/pdf") mustEqual `Content-Type`(`application/pdf`)
    )
    "be parsed correctly from example 2" in (
      HttpHeader("Content-Type", "text/plain; charset=utf8") mustEqual `Content-Type`(ContentType(`text/plain`, `UTF-8`))
    )
    "be parsed correctly from example 3" in (
      HttpHeader("Content-Type", "text/css") mustEqual `Content-Type`(ContentType(`text/css`, `ISO-8859-1`))
    )
  }
  
  "Header 'X-Forwarded-For'" should {
    "be parsed correctly from example 1" in (
      HttpHeader("X-Forwarded-For", "1.2.3.4") mustEqual `X-Forwarded-For`("1.2.3.4")
    )
    "be parsed correctly from example 2" in (
      HttpHeader("X-Forwarded-For", "234.123.5.6 , 8.8.8.8") mustEqual `X-Forwarded-For`("234.123.5.6", "8.8.8.8")
    )
  }
  
  "Header 'CustomHeader'" should {
    "be parsed correctly from example 1" in (
      HttpHeader("X-Space-Ranger", "no, this rock!") mustEqual CustomHeader("X-Space-Ranger", "no, this rock!")
    )
  }
}