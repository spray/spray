/*
 * Copyright (C) 2011-2012 spray.io
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

package spray
package http

import java.lang.String

abstract class HttpHeader {
  def name: String
  def lowercaseName: String
  def value: String
  def is(nameInLowerCase: String): Boolean = lowercaseName == nameInLowerCase
  def isNot(nameInLowerCase: String): Boolean = lowercaseName != nameInLowerCase
  override def toString = name + ": " + value
}

object HttpHeaders {

  object Accept { def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more) }
  case class Accept(mediaRanges: Seq[MediaRange]) extends HttpHeader {
    def name = "Accept"
    def lowercaseName = "accept"
    def value = mediaRanges.map(_.value).mkString(", ")
  }
  
  object `Accept-Charset` { def apply(first: HttpCharsetRange, more: HttpCharsetRange*): `Accept-Charset` = apply(first +: more) }
  case class `Accept-Charset`(charsetRanges: Seq[HttpCharsetRange]) extends HttpHeader {
    def name = "Accept-Charset"
    def lowercaseName = "accept-charset"
    def value = charsetRanges.map(_.value).mkString(", ")
  }
  
  object `Accept-Encoding` { def apply(first: HttpEncodingRange, more: HttpEncodingRange*): `Accept-Encoding` = apply(first +: more) }
  case class `Accept-Encoding`(encodings: Seq[HttpEncodingRange]) extends HttpHeader {
    def name = "Accept-Encoding"
    def lowercaseName = "accept-encoding"
    def value = encodings.map(_.value).mkString(", ")
  }
  
  object `Accept-Language` { def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(first +: more) }
  case class `Accept-Language`(languageRanges: Seq[LanguageRange]) extends HttpHeader {
    def name = "Accept-Language"
    def lowercaseName = "accept-language"
    def value = languageRanges.map(_.value).mkString(", ")
  }
  
  object `Accept-Ranges` { def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more) }
  case class `Accept-Ranges`(rangeUnits: Seq[RangeUnit]) extends HttpHeader {
    def name = "Accept-Ranges"
    def lowercaseName = "accept-ranges"
    def value = if (rangeUnits.isEmpty) "none" else rangeUnits.mkString(", ")
  }
  
  case class Authorization(credentials: HttpCredentials) extends HttpHeader {
    def name = "Authorization"
    def lowercaseName = "authorization"
    def value = credentials.value
  }

  object `Cache-Control` { def apply(first: CacheDirective, more: CacheDirective*): `Cache-Control` = apply(first +: more) }
  case class `Cache-Control`(directives: Seq[CacheDirective]) extends HttpHeader {
    def name = "Cache-Control"
    def lowercaseName = "cache-control"
    def value = directives.mkString(", ")
  }

  object Connection { def apply(first: String, more: String*): `Connection` = apply(first +: more) }
  case class Connection(connectionTokens: Seq[String]) extends HttpHeader {
    def name = "Connection"
    def lowercaseName = "connection"
    def value = connectionTokens.mkString(", ")
    def hasClose = connectionTokens.exists(_.toLowerCase == "close")
    def hasKeepAlive = connectionTokens.exists(_.toLowerCase == "keep-alive")
  }

  // see http://tools.ietf.org/html/rfc2183
  case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String]) extends HttpHeader {
    def name = "Content-Disposition"
    def lowercaseName = "content-disposition"
    def value = parameters.map(p => "; " + p._1 + "=\"" + p._2 + '"').mkString(dispositionType, "", "")
  }

  case class `Content-Encoding`(encoding: HttpEncoding) extends HttpHeader {
    def name = "Content-Encoding"
    def lowercaseName = "content-encoding"
    def value = encoding.value
  }
  
  case class `Content-Length`(length: Int) extends HttpHeader {
    def name = "Content-Length"
    def lowercaseName = "content-length"
    def value = length.toString
  }
  
  case class `Content-Type`(contentType: ContentType) extends HttpHeader {
    def name = "Content-Type"
    def lowercaseName = "content-type"
    def value = contentType.value
  }

  object Cookie { def apply(first: HttpCookie, more: HttpCookie*): `Cookie` = apply(first +: more) }
  case class Cookie(cookies: Seq[HttpCookie]) extends HttpHeader {
    def name = "Cookie"
    def lowercaseName = "cookie"
    def value = cookies.mkString("; ")
  }
  
  case class Date(date: DateTime) extends HttpHeader {
    def name = "Date"
    def lowercaseName = "date"
    def value = date.toRfc1123DateTimeString
  }

  case class Host(host: String, port: Option[Int] = None) extends HttpHeader {
    def name = "Host"
    def lowercaseName = "host"
    def value = port.map(host + ':' + _).getOrElse(host)
  }

  case class `Last-Modified`(date: DateTime) extends HttpHeader {
    def name = "Last-Modified"
    def lowercaseName = "last-modified"
    def value = date.toRfc1123DateTimeString
  }
  
  case class Location(absoluteUri: String) extends HttpHeader {
    def name = "Location"
    def lowercaseName = "location"
    def value = absoluteUri
  }

  case class `Remote-Address`(ip: HttpIp) extends HttpHeader {
    def name = "Remote-Address"
    def lowercaseName = "remote-address"
    def value = ip.value
  }

  case class `Set-Cookie`(cookie: HttpCookie) extends HttpHeader {
    def name = "Set-Cookie"
    def lowercaseName = "set-cookie"
    def value = cookie.value
  }

  object `WWW-Authenticate` { def apply(first: HttpChallenge, more: HttpChallenge*): `WWW-Authenticate` = apply(first +: more) }
  case class `WWW-Authenticate`(challenges: Seq[HttpChallenge]) extends HttpHeader {
    def name = "WWW-Authenticate"
    def lowercaseName = "www-authenticate"
    def value = challenges.mkString(", ")
  }
  
  object `X-Forwarded-For` { def apply(first: HttpIp, more: HttpIp*): `X-Forwarded-For` = apply(first +: more) }
  case class `X-Forwarded-For`(ips: Seq[HttpIp]) extends HttpHeader {
    def name = "X-Forwarded-For"
    def lowercaseName = "x-forwarded-for"
    def value = ips.mkString(", ")
  }

  case class RawHeader(name: String, value: String) extends HttpHeader {
    val lowercaseName = name.toLowerCase
  }
}