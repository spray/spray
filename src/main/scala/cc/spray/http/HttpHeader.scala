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
import org.parboiled.scala.rules.Rule1

trait HttpHeader extends Product {
  val name = unmangle(productPrefix)
  def value: String
  override def toString = name + ": " + value
}

object HttpHeader {
  private var rules = Map.empty[String, Option[Rule1[HttpHeader]]]

  private def getRule(headerName: String): Option[Rule1[HttpHeader]] = {
    rules.get(headerName) match {
      case Some(ruleOption) => ruleOption
      case None => {
        val ruleOption = {
          try {
            val method = HttpParser.getClass.getMethod(headerName.trim.toUpperCase.replace('-', '_'))
            Some(method.invoke(HttpParser).asInstanceOf[Rule1[HttpHeader]])
          } catch {
            case _: NoSuchMethodException => None
          }
        }
        // unsynchronized write, we accept the small chance that we overwrite a just previously written value
        // and loose some cache efficiency, however we do save the cost of synchronization for all accesses
        rules = rules.updated(headerName, ruleOption)
        ruleOption
      }
    }
  }
  
  def apply(name: String, value: String): HttpHeader = {
    getRule(name) match {
      case None => HttpHeaders.CustomHeader(name, value)
      case Some(rule) => {
        HttpParser.parse(rule, value) match {
          case Left(error) => throw new HttpException(HttpStatusCodes.BadRequest, 
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
  
  object `Accept-Charset` { def apply(first: CharsetRange, more: CharsetRange*): `Accept-Charset` = apply(first +: more) }
  case class `Accept-Charset`(charsetRanges: Seq[CharsetRange]) extends HttpHeader {
    def value = charsetRanges.mkString(", ")
  }
  
  object `Accept-Encoding` { def apply(first: EncodingRange, more: EncodingRange*): `Accept-Encoding` = apply(first +: more) }
  case class `Accept-Encoding`(encodings: Seq[EncodingRange]) extends HttpHeader {
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
  
  case class `Authorization`(credentials: Credentials) extends HttpHeader {
    def value = credentials.value
  }
  
  case class `Connection`(connectionToken: ConnectionToken) extends HttpHeader {
    def value = connectionToken.value
  }
  
  case class `Content-Encoding`(encoding: Encoding) extends HttpHeader {
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