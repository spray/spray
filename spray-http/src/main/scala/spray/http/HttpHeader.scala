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

import scala.annotation.{ implicitNotFound, tailrec }
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

  object ProtectedHeaderCreation {
    @implicitNotFound("Headers of this type are managed automatically by spray. If you are sure that creating instances " +
      "manually is required in your use case `import HttpHeaders.ProtectedHeaderCreation.enable` to override this warning.")
    sealed trait Enabled
    implicit def enable: Enabled = null
  }
  import ProtectedHeaderCreation.enable

  sealed abstract class ModeledCompanion extends Renderable {
    val name = {
      val n = getClass.getName
      n.substring(n.indexOf('$') + 1, n.length - 1).replace("$minus", "-")
    }
    val lowercaseName = name.toLowerCase
    private[this] val nameBytes = asciiBytes(name)
    def render[R <: Rendering](r: R): R = r ~~ nameBytes ~~ ':' ~~ ' '
  }

  abstract class ModeledHeader extends HttpHeader with Serializable {
    def name: String = companion.name
    def value: String = renderValue(new StringRendering).get
    def lowercaseName: String = companion.lowercaseName
    def render[R <: Rendering](r: R): R = renderValue(r ~~ companion)
    def renderValue[R <: Rendering](r: R): R
    protected def companion: ModeledCompanion
  }

  object Accept extends ModeledCompanion {
    def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[MediaRange] // cache
  }
  case class Accept(mediaRanges: Seq[MediaRange]) extends ModeledHeader {
    import Accept.rangesRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ mediaRanges
    protected def companion = Accept
  }

  object `Accept-Charset` extends ModeledCompanion {
    def apply(first: HttpCharsetRange, more: HttpCharsetRange*): `Accept-Charset` = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[HttpCharsetRange] // cache
  }
  case class `Accept-Charset`(charsetRanges: Seq[HttpCharsetRange]) extends ModeledHeader {
    import `Accept-Charset`.rangesRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ charsetRanges
    protected def companion = `Accept-Charset`
  }

  object `Accept-Encoding` extends ModeledCompanion {
    def apply(first: HttpEncodingRange, more: HttpEncodingRange*): `Accept-Encoding` = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[HttpEncodingRange] // cache
  }
  case class `Accept-Encoding`(encodings: Seq[HttpEncodingRange]) extends ModeledHeader {
    import `Accept-Encoding`.rangesRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ encodings
    protected def companion = `Accept-Encoding`
  }

  object `Accept-Language` extends ModeledCompanion {
    def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[LanguageRange] // cache
  }
  case class `Accept-Language`(languages: Seq[LanguageRange]) extends ModeledHeader {
    import `Accept-Language`.rangesRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ languages
    protected def companion = `Accept-Language`
  }

  object Authorization extends ModeledCompanion
  case class Authorization(credentials: HttpCredentials) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = r ~~ credentials
    protected def companion = Authorization
  }

  object `Cache-Control` extends ModeledCompanion {
    def apply(first: CacheDirective, more: CacheDirective*): `Cache-Control` = apply(first +: more)
    implicit val directivesRenderer = Renderer.defaultSeqRenderer[CacheDirective] // cache
  }
  case class `Cache-Control`(directives: Seq[CacheDirective]) extends ModeledHeader {
    import `Cache-Control`.directivesRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ directives
    protected def companion = `Cache-Control`
  }

  object Connection extends ModeledCompanion {
    def apply(first: String, more: String*): Connection = apply(first +: more)
    implicit val tokensRenderer = Renderer.defaultSeqRenderer[String] // cache
  }
  case class Connection(tokens: Seq[String]) extends ModeledHeader {
    import Connection.tokensRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ tokens
    def hasClose = has("close")
    def hasKeepAlive = has("keep-alive")
    @tailrec private def has(item: String, ix: Int = 0): Boolean =
      if (ix < tokens.length)
        if (tokens(ix) equalsIgnoreCase item) true
        else has(item, ix + 1)
      else false
    protected def companion = Connection
  }

  // see http://tools.ietf.org/html/rfc2183
  object `Content-Disposition` extends ModeledCompanion
  case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String] = Map.empty) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = {
      r ~~ dispositionType
      if (parameters.nonEmpty) parameters foreach { case (k, v) ⇒ r ~~ ';' ~~ ' ' ~~ k ~~ '=' ~~# v }
      r
    }
    protected def companion = `Content-Disposition`
  }

  object `Content-Encoding` extends ModeledCompanion
  case class `Content-Encoding`(encoding: HttpEncoding) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = r ~~ encoding
    protected def companion = `Content-Encoding`
  }

  object `Content-Length` extends ModeledCompanion
  case class `Content-Length`(length: Int)(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = r ~~ length
    protected def companion = `Content-Length`
  }

  object `Content-Type` extends ModeledCompanion
  case class `Content-Type`(contentType: ContentType)(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = r ~~ contentType
    protected def companion = `Content-Type`
  }

  object Cookie extends ModeledCompanion {
    def apply(first: HttpCookie, more: HttpCookie*): `Cookie` = apply(first +: more)
    implicit val cookiesRenderer = Renderer.seqRenderer[String, HttpCookie](separator = "; ") // cache
  }
  case class Cookie(cookies: Seq[HttpCookie]) extends ModeledHeader {
    import Cookie.cookiesRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ cookies
    protected def companion = Cookie
  }

  object Date extends ModeledCompanion
  case class Date(date: DateTime)(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = date.renderRfc1123DateTimeString(r)
    protected def companion = Date
  }

  object Expect extends ModeledCompanion {
    def apply(first: String, more: String*): Expect = apply(first +: more)
    implicit val expectationsRenderer = Renderer.defaultSeqRenderer[String] // cache
  }
  case class Expect(expectations: Seq[String]) extends ModeledHeader {
    import Expect.expectationsRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ expectations
    def has100continue = expectations.exists(_ equalsIgnoreCase "100-continue")
    protected def companion = Expect
  }

  object Host extends ModeledCompanion {
    def apply(address: InetSocketAddress): Host = apply(address.getHostName, address.getPort)
  }
  case class Host(host: String, port: Int = 0) extends ModeledHeader {
    require((port >> 16) == 0, "Illegal port: " + port)
    def renderValue[R <: Rendering](r: R): R = if (port > 0) r ~~ host ~~ ':' ~~ port else r ~~ host
    protected def companion = Host
  }

  object `Last-Modified` extends ModeledCompanion
  case class `Last-Modified`(date: DateTime) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = date.renderRfc1123DateTimeString(r)
    protected def companion = `Last-Modified`
  }

  object Location extends ModeledCompanion
  case class Location(uri: Uri) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = r ~~ uri
    protected def companion = Location
  }

  object `Remote-Address` extends ModeledCompanion
  case class `Remote-Address`(ip: HttpIp) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = r ~~ ip
    protected def companion = `Remote-Address`
  }

  object Server extends ModeledCompanion {
    def apply(products: String): Server = apply(ProductVersion.parseMultiple(products))
    def apply(first: ProductVersion, more: ProductVersion*): Server = apply(first +: more)
    implicit val productsRenderer = Renderer.seqRenderer[Char, ProductVersion](separator = ' ') // cache
  }
  case class Server(products: Seq[ProductVersion])(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    import Server.productsRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ products
    protected def companion = Server
  }

  object `Set-Cookie` extends ModeledCompanion
  case class `Set-Cookie`(cookie: HttpCookie) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): R = r ~~ cookie
    protected def companion = `Set-Cookie`
  }

  object `Transfer-Encoding` extends ModeledCompanion {
    def apply(first: String, more: String*): `Transfer-Encoding` = apply(first +: more)
    implicit val encodingsRenderer = Renderer.defaultSeqRenderer[String] // cache
  }
  case class `Transfer-Encoding`(encodings: Seq[String])(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    import `Transfer-Encoding`.encodingsRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ encodings
    def hasChunked: Boolean = {
      @tailrec def rec(ix: Int = 0): Boolean =
        if (ix < encodings.size)
          if (encodings(ix) equalsIgnoreCase "chunked") true
          else rec(ix + 1)
        else false
      rec()
    }
    protected def companion = `Transfer-Encoding`
  }

  object `User-Agent` extends ModeledCompanion {
    def apply(products: String): `User-Agent` = apply(ProductVersion.parseMultiple(products))
    def apply(first: ProductVersion, more: ProductVersion*): `User-Agent` = apply(first +: more)
    implicit val productsRenderer = Renderer.seqRenderer[Char, ProductVersion](separator = ' ') // cache
  }
  case class `User-Agent`(products: Seq[ProductVersion])(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    import `User-Agent`.productsRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ products
    protected def companion = `User-Agent`
  }

  object `WWW-Authenticate` extends ModeledCompanion {
    def apply(first: HttpChallenge, more: HttpChallenge*): `WWW-Authenticate` = apply(first +: more)
    implicit val challengesRenderer = Renderer.defaultSeqRenderer[HttpChallenge] // cache
  }
  case class `WWW-Authenticate`(challenges: Seq[HttpChallenge]) extends ModeledHeader {
    import `WWW-Authenticate`.challengesRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ challenges
    protected def companion = `WWW-Authenticate`
  }

  object `X-Forwarded-For` extends ModeledCompanion {
    def apply(first: HttpIp, more: HttpIp*): `X-Forwarded-For` = apply((first +: more).map(Some(_)))
    implicit val ipsRenderer = Renderer.defaultSeqRenderer[Option[HttpIp]](Renderer.optionRenderer("unknown"))
  }
  case class `X-Forwarded-For`(ips: Seq[Option[HttpIp]]) extends ModeledHeader {
    import `X-Forwarded-For`.ipsRenderer
    def renderValue[R <: Rendering](r: R): R = r ~~ ips
    protected def companion = `X-Forwarded-For`
  }

  case class RawHeader(name: String, value: String) extends HttpHeader {
    val lowercaseName = name.toLowerCase
    def render[R <: Rendering](r: R): R = r ~~ name ~~ ':' ~~ ' ' ~~ value
  }
}
