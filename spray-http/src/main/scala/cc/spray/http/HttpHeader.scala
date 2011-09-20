/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package http

import parser.HttpParser

trait HttpHeader extends Product {
  val name = productPrefix.replace("$minus", "-")
  def value: String
  override def toString = name + ": " + value
}

object HttpHeader {
  def apply(name: String, value: String): HttpHeader = {
    HttpParser.rules.get(name.trim.toUpperCase.replace('-', '_')) match {
      case None => HttpHeaders.CustomHeader(name, value)
      case Some(rule) => {
        HttpParser.parse(rule, value) match {
          case Left(error) => throw new HttpException(StatusCodes.BadRequest, 
            "Illegal HTTP header '" + name + "':\n" + error)
          case Right(header) => header
        }
      } 
    }
  }
  
  def unapply(header: HttpHeader): Option[(String, String)] = Some(header.name -> header.value) 
}

object HttpHeaders {

  object Accept { def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more) }
  case class `Accept`(mediaRanges: Seq[MediaRange]) extends HttpHeader {
    def value = mediaRanges.mkString(", ")
  }
  
  object `Accept-Charset` { def apply(first: HttpCharsetRange, more: HttpCharsetRange*): `Accept-Charset` = apply(first +: more) }
  case class `Accept-Charset`(charsetRanges: Seq[HttpCharsetRange]) extends HttpHeader {
    def value = charsetRanges.mkString(", ")
  }
  
  object `Accept-Encoding` { def apply(first: HttpEncodingRange, more: HttpEncodingRange*): `Accept-Encoding` = apply(first +: more) }
  case class `Accept-Encoding`(encodings: Seq[HttpEncodingRange]) extends HttpHeader {
    def value = encodings.mkString(", ")
  }
  
  object `Accept-Language` { def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(first +: more) }
  case class `Accept-Language`(languageRanges: Seq[LanguageRange]) extends HttpHeader {
    def value = languageRanges.mkString(", ")
  }
  
  object `Accept-Ranges` { def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more) }
  case class `Accept-Ranges`(rangeUnits: Seq[RangeUnit]) extends HttpHeader {
    def value = if (rangeUnits.isEmpty) "none" else rangeUnits.mkString(", ")
  }
  
  case class `Authorization`(credentials: HttpCredentials) extends HttpHeader {
    def value = credentials.value
  }

  object `Cache-Control` { def apply(first: CacheDirective, more: CacheDirective*): `Cache-Control` = apply(first +: more) }
  case class `Cache-Control`(directives: Seq[CacheDirective]) extends HttpHeader {
    def value = directives.mkString(", ")
  }

  object `Connection` { def apply(first: ConnectionToken, more: ConnectionToken*): `Connection` = apply(first +: more) }
  case class `Connection`(connectionTokens: Seq[ConnectionToken]) extends HttpHeader {
    def value = connectionTokens.mkString(", ")
  }
  
  case class `Content-Encoding`(encoding: HttpEncoding) extends HttpHeader {
    def value = encoding.value
  }
  
  case class `Content-Length`(length: Int) extends HttpHeader {
    def value = length.toString
  }
  
  case class `Content-Type`(contentType: ContentType) extends HttpHeader {
    def value = contentType.value
  }

  object `Cookie` { def apply(first: HttpCookie, more: HttpCookie*): `Cookie` = apply(first +: more) }
  case class `Cookie`(cookies: Seq[HttpCookie]) extends HttpHeader {
    def value = cookies.mkString("; ")
  }
  
  case class `Date`(date: DateTime) extends HttpHeader {
    def value = date.toRfc1123DateTimeString
  }
  
  case class `Location`(absoluteUri: String) extends HttpHeader {
    def value = absoluteUri
  }

  case class `Set-Cookie`(cookie: HttpCookie) extends HttpHeader {
    def value = cookie.value
  }

  object `WWW-Authenticate` { def apply(first: HttpChallenge, more: HttpChallenge*): `WWW-Authenticate` = apply(first +: more) }
  case class `WWW-Authenticate`(challenges: Seq[HttpChallenge]) extends HttpHeader {
    def value = challenges.mkString(", ")
  }
  
  object `X-Forwarded-For` { def apply(first: HttpIp, more: HttpIp*): `X-Forwarded-For` = apply(first +: more) }
  case class `X-Forwarded-For`(ips: Seq[HttpIp]) extends HttpHeader {
    def value = ips.mkString(", ")
  }
  
  case class `CustomHeader`(override val name: String, value: String) extends HttpHeader

  /////////////////////////////////////////////////////////////////////////////////////////

  def parseFromRaw(rawHeaders: List[Product2[String, String]]) = {
    import collection.mutable.ListBuffer
    var contentType: Option[`Content-Type`] = None
    var contentLength: Option[`Content-Length`] = None
    val filtered = ListBuffer.empty[HttpHeader]
    rawHeaders.foreach { raw =>
      HttpHeader(raw._1, raw._2) match {
        case x:`Content-Type` => contentType = Some(x)
        case x:`Content-Length` => contentLength = Some(x)
        case x => filtered += x
      }
    }
    (contentType, contentLength, filtered.toList)
  }
}