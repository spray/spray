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
  
  case class `Connection`(connectionToken: ConnectionToken) extends HttpHeader {
    def value = connectionToken.value
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
  
  case class `Date`(rfc1123Date: String) extends HttpHeader {
    def value = rfc1123Date
  }
  
  case class `Location`(absoluteUri: String) extends HttpHeader {
    def value = absoluteUri
  }
  
  case class `WWW-Authenticate`(scheme: String, realm: String, params: Map[String, String] = Map.empty) extends HttpHeader {
    def value = {
      scheme + ' ' + (("realm" -> realm) :: params.toList).map { case (k, v) => k + "=\"" + v + '"' }.mkString(",")
    }
  }
  
  object `X-Forwarded-For` { def apply(first: HttpIp, more: HttpIp*): `X-Forwarded-For` = apply(first +: more) }
  case class `X-Forwarded-For`(ips: Seq[HttpIp]) extends HttpHeader {
    def value = ips.mkString(", ")
  }
  
  case class `CustomHeader`(override val name: String, value: String) extends HttpHeader
}