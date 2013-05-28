/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.http

import scala.annotation.tailrec
import java.net.InetSocketAddress

abstract class HttpHeader extends ToStringRenderable {
  def name: String
  def value: String
  def lowercaseName: String
  def is(nameInLowerCase: String): Boolean = lowercaseName == nameInLowerCase
  def isNot(nameInLowerCase: String): Boolean = lowercaseName != nameInLowerCase
}

object HttpHeader {
  def unapply(header: HttpHeader): Option[(String, String)] = Some((header.lowercaseName, header.value))
}

object HttpHeaders {

  sealed abstract class ModeledCompanion extends Renderable {
    val name = {
      val n = getClass.getName
      n.substring(n.indexOf('$') + 1, n.length - 1).replace("$minus", "-")
    }
    val lowercaseName = name.toLowerCase
    private[this] val nameBytes = asciiBytes(name)
    def render[R <: Rendering](r: R): r.type = r ~~ nameBytes ~~ ':' ~~ ' '
  }

  abstract class ModeledHeader(companion: ModeledCompanion) extends HttpHeader {
    def name: String = companion.name
    def value: String = renderValue(new StringRendering).get
    def lowercaseName: String = companion.lowercaseName
    def render[R <: Rendering](r: R): r.type = renderValue(r ~~ companion)
    def renderValue[R <: Rendering](r: R): r.type
  }

  object Accept extends ModeledCompanion {
    def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[MediaRange] // cache
  }
  case class Accept(mediaRanges: Seq[MediaRange]) extends ModeledHeader(Accept) {
    import Accept.rangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ mediaRanges
  }

  object `Accept-Charset` extends ModeledCompanion {
    def apply(first: HttpCharsetRange, more: HttpCharsetRange*): `Accept-Charset` = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[HttpCharsetRange] // cache
  }
  case class `Accept-Charset`(charsetRanges: Seq[HttpCharsetRange]) extends ModeledHeader(`Accept-Charset`) {
    import `Accept-Charset`.rangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ charsetRanges
  }

  object `Accept-Encoding` extends ModeledCompanion {
    def apply(first: HttpEncodingRange, more: HttpEncodingRange*): `Accept-Encoding` = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[HttpEncodingRange] // cache
  }
  case class `Accept-Encoding`(encodings: Seq[HttpEncodingRange]) extends ModeledHeader(`Accept-Encoding`) {
    import `Accept-Encoding`.rangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ encodings
  }

  object `Accept-Language` extends ModeledCompanion {
    def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[LanguageRange] // cache
  }
  case class `Accept-Language`(languages: Seq[LanguageRange]) extends ModeledHeader(`Accept-Language`) {
    import `Accept-Language`.rangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ languages
  }

  object Authorization extends ModeledCompanion
  case class Authorization(credentials: HttpCredentials) extends ModeledHeader(Authorization) {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ credentials
  }

  object `Cache-Control` extends ModeledCompanion {
    def apply(first: CacheDirective, more: CacheDirective*): `Cache-Control` = apply(first +: more)
    implicit val directivesRenderer = Renderer.defaultSeqRenderer[CacheDirective] // cache
  }
  case class `Cache-Control`(directives: Seq[CacheDirective]) extends ModeledHeader(`Cache-Control`) {
    import `Cache-Control`.directivesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ directives
  }

  object Connection extends ModeledCompanion {
    def apply(first: String, more: String*): Connection = apply(first +: more)
    implicit val tokensRenderer = Renderer.defaultSeqRenderer[String] // cache
  }
  case class Connection(tokens: Seq[String]) extends ModeledHeader(Connection) {
    import Connection.tokensRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ tokens
    def hasClose = has("close")
    def hasKeepAlive = has("keep-alive")
    @tailrec private def has(item: String, ix: Int = 0): Boolean =
      if (ix < tokens.length)
        if (tokens(ix) equalsIgnoreCase item) true
        else has(item, ix + 1)
      else false
  }

  // see http://tools.ietf.org/html/rfc2183
  object `Content-Disposition` extends ModeledCompanion
  case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String] = Map.empty)
      extends ModeledHeader(`Content-Disposition`) {
    def renderValue[R <: Rendering](r: R): r.type = {
      r ~~ dispositionType
      if (parameters.nonEmpty) parameters foreach { case (k, v) ⇒ r ~~ ';' ~~ ' ' ~~ k ~~ '=' ~~# v }
      r
    }
  }

  object `Content-Encoding` extends ModeledCompanion
  case class `Content-Encoding`(encoding: HttpEncoding) extends ModeledHeader(`Content-Encoding`) {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ encoding
  }

  object `Content-Length` extends ModeledCompanion
  case class `Content-Length`(length: Int) extends ModeledHeader(`Content-Length`) {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ length
  }

  object `Content-Type` extends ModeledCompanion
  case class `Content-Type`(contentType: ContentType) extends ModeledHeader(`Content-Type`) {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ contentType
  }

  object Cookie extends ModeledCompanion {
    def apply(first: HttpCookie, more: HttpCookie*): `Cookie` = apply(first +: more)
    implicit val cookiesRenderer = Renderer.seqRenderer[String, HttpCookie](separator = "; ") // cache
  }
  case class Cookie(cookies: Seq[HttpCookie]) extends ModeledHeader(Cookie) {
    import Cookie.cookiesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ cookies
  }

  object Date extends ModeledCompanion
  case class Date(date: DateTime) extends ModeledHeader(Date) {
    def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
  }

  object Expect extends ModeledCompanion {
    def apply(first: String, more: String*): Expect = apply(first +: more)
    implicit val expectationsRenderer = Renderer.defaultSeqRenderer[String] // cache
  }
  case class Expect(expectations: Seq[String]) extends ModeledHeader(Expect) {
    import Expect.expectationsRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ expectations
    def has100continue = expectations.exists(_ equalsIgnoreCase "100-continue")
  }

  object Host extends ModeledCompanion {
    def apply(address: InetSocketAddress): Host = apply(address.getHostName, address.getPort)
  }
  case class Host(host: String, port: Int = 0) extends ModeledHeader(Host) {
    require((port >> 16) == 0, "Illegal port: " + port)
    def renderValue[R <: Rendering](r: R): r.type = if (port > 0) r ~~ host ~~ ':' ~~ port else r ~~ host
  }

  object `Last-Modified` extends ModeledCompanion
  case class `Last-Modified`(date: DateTime) extends ModeledHeader(`Last-Modified`) {
    def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
  }

  object Location extends ModeledCompanion
  case class Location(uri: Uri) extends ModeledHeader(Location) {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ uri
  }

  object `Remote-Address` extends ModeledCompanion
  case class `Remote-Address`(ip: HttpIp) extends ModeledHeader(`Remote-Address`) {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ ip
  }

  object Server extends ModeledCompanion {
    def apply(products: String): Server = apply(ProductVersion.parseMultiple(products))
    def apply(first: ProductVersion, more: ProductVersion*): Server = apply(first +: more)
    implicit val productsRenderer = Renderer.seqRenderer[Char, ProductVersion](separator = ' ') // cache
  }
  case class Server(products: Seq[ProductVersion]) extends ModeledHeader(Server) {
    import Server.productsRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ products
  }

  object `Set-Cookie` extends ModeledCompanion
  case class `Set-Cookie`(cookie: HttpCookie) extends ModeledHeader(`Set-Cookie`) {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ cookie
  }

  object `Transfer-Encoding` extends ModeledCompanion {
    def apply(first: String, more: String*): `Transfer-Encoding` = apply(first +: more)
    implicit val encodingsRenderer = Renderer.defaultSeqRenderer[String] // cache
  }
  case class `Transfer-Encoding`(encodings: Seq[String]) extends ModeledHeader(`Transfer-Encoding`) {
    import `Transfer-Encoding`.encodingsRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ encodings
    def hasChunked: Boolean = {
      @tailrec def rec(ix: Int = 0): Boolean =
        if (ix < encodings.size)
          if (encodings(ix) equalsIgnoreCase "chunked") true
          else rec(ix + 1)
        else false
      rec()
    }
  }

  object `User-Agent` extends ModeledCompanion {
    def apply(products: String): `User-Agent` = apply(ProductVersion.parseMultiple(products))
    def apply(first: ProductVersion, more: ProductVersion*): `User-Agent` = apply(first +: more)
    implicit val productsRenderer = Renderer.seqRenderer[Char, ProductVersion](separator = ' ') // cache
  }
  case class `User-Agent`(products: Seq[ProductVersion]) extends ModeledHeader(`User-Agent`) {
    import `User-Agent`.productsRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ products
  }

  object `WWW-Authenticate` extends ModeledCompanion {
    def apply(first: HttpChallenge, more: HttpChallenge*): `WWW-Authenticate` = apply(first +: more)
    implicit val challengesRenderer = Renderer.defaultSeqRenderer[HttpChallenge] // cache
  }
  case class `WWW-Authenticate`(challenges: Seq[HttpChallenge]) extends ModeledHeader(`WWW-Authenticate`) {
    import `WWW-Authenticate`.challengesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ challenges
  }

  object `X-Forwarded-For` extends ModeledCompanion {
    def apply(first: HttpIp, more: HttpIp*): `X-Forwarded-For` = apply((first +: more).map(Some(_)))
    implicit val ipsRenderer = Renderer.defaultSeqRenderer[Option[HttpIp]](Renderer.optionRenderer("unknown"))
  }
  case class `X-Forwarded-For`(ips: Seq[Option[HttpIp]]) extends ModeledHeader(`X-Forwarded-For`) {
    import `X-Forwarded-For`.ipsRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ ips
  }

  case class RawHeader(name: String, value: String) extends HttpHeader {
    val lowercaseName = name.toLowerCase
    def render[R <: Rendering](r: R): r.type = r ~~ name ~~ ':' ~~ ' ' ~~ value
  }
}