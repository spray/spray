/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import spray.util._

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
    private[this] val nameBytes = name.getAsciiBytes
    def render[R <: Rendering](r: R): r.type = r ~~ nameBytes ~~ ':' ~~ ' '
  }

  sealed abstract class ModeledHeader extends HttpHeader with Serializable {
    def name: String = companion.name
    def value: String = renderValue(new StringRendering).get
    def lowercaseName: String = companion.lowercaseName
    def render[R <: Rendering](r: R): r.type = renderValue(r ~~ companion)
    def renderValue[R <: Rendering](r: R): r.type
    protected def companion: ModeledCompanion
  }

  object Accept extends ModeledCompanion {
    def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[MediaRange] // cache
  }
  case class Accept(mediaRanges: Seq[MediaRange]) extends ModeledHeader {
    import Accept.rangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ mediaRanges
    protected def companion = Accept
  }

  object `Accept-Charset` extends ModeledCompanion {
    def apply(first: HttpCharsetRange, more: HttpCharsetRange*): `Accept-Charset` = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[HttpCharsetRange] // cache
  }
  case class `Accept-Charset`(charsetRanges: Seq[HttpCharsetRange]) extends ModeledHeader {
    import `Accept-Charset`.rangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ charsetRanges
    protected def companion = `Accept-Charset`
  }

  object `Accept-Encoding` extends ModeledCompanion {
    def apply(first: HttpEncodingRange, more: HttpEncodingRange*): `Accept-Encoding` = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[HttpEncodingRange] // cache
  }
  case class `Accept-Encoding`(encodings: Seq[HttpEncodingRange]) extends ModeledHeader {
    import `Accept-Encoding`.rangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ encodings
    protected def companion = `Accept-Encoding`
  }

  object `Accept-Language` extends ModeledCompanion {
    def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(first +: more)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[LanguageRange] // cache
  }
  case class `Accept-Language`(languages: Seq[LanguageRange]) extends ModeledHeader {
    import `Accept-Language`.rangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ languages
    protected def companion = `Accept-Language`
  }

  object `Access-Control-Allow-Credentials` extends ModeledCompanion
  case class `Access-Control-Allow-Credentials`(allow: Boolean) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ allow.toString
    protected def companion = `Access-Control-Allow-Credentials`
  }
  object `Access-Control-Allow-Headers` extends ModeledCompanion {
    def apply(first: String, more: String*): `Access-Control-Allow-Headers` = apply(first +: more)
    implicit val headersRenderer = Renderer.defaultSeqRenderer[String]
  }
  case class `Access-Control-Allow-Headers`(headers: Seq[String]) extends ModeledHeader {
    import `Access-Control-Allow-Headers`.headersRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ headers
    protected def companion = `Access-Control-Allow-Headers`
  }

  object `Access-Control-Allow-Methods` extends ModeledCompanion {
    def apply(first: HttpMethod, more: HttpMethod*): `Access-Control-Allow-Methods` = apply(first +: more)
    implicit val methodsRenderer = Renderer.defaultSeqRenderer[HttpMethod]
  }
  case class `Access-Control-Allow-Methods`(methods: Seq[HttpMethod]) extends ModeledHeader {
    import `Access-Control-Allow-Methods`.methodsRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ methods
    protected def companion = `Access-Control-Allow-Methods`
  }

  object `Access-Control-Allow-Origin` extends ModeledCompanion
  case class `Access-Control-Allow-Origin`(allowedOrigins: AllowedOrigins) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ allowedOrigins
    protected def companion = `Access-Control-Allow-Origin`
  }

  object `Access-Control-Request-Headers` extends ModeledCompanion {
    def apply(first: String, more: String*): `Access-Control-Request-Headers` = apply(first +: more)
    implicit val headersRenderer = Renderer.defaultSeqRenderer[String]
  }
  case class `Access-Control-Request-Headers`(headers: Seq[String]) extends ModeledHeader {
    import `Access-Control-Request-Headers`.headersRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ headers
    protected def companion = `Access-Control-Request-Headers`
  }

  object `Access-Control-Request-Method` extends ModeledCompanion
  case class `Access-Control-Request-Method`(method: HttpMethod) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ method
    protected def companion = `Access-Control-Request-Method`
  }

  object `Access-Control-Expose-Headers` extends ModeledCompanion {
    def apply(first: String, more: String*): `Access-Control-Expose-Headers` = apply(first +: more)
    implicit val headersRenderer = Renderer.defaultSeqRenderer[String]
  }
  case class `Access-Control-Expose-Headers`(headers: Seq[String]) extends ModeledHeader {
    import `Access-Control-Expose-Headers`.headersRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ headers
    protected def companion = `Access-Control-Expose-Headers`
  }

  object `Access-Control-Max-Age` extends ModeledCompanion
  case class `Access-Control-Max-Age`(deltaSeconds: Long) extends ModeledHeader {
    require(deltaSeconds >= 0, "deltaSeconds must be >= 0")
    def renderValue[R <: Rendering](r: R): r.type = r ~~ deltaSeconds
    protected def companion = `Access-Control-Max-Age`
  }

  object Allow extends ModeledCompanion {
    implicit val methodsRenderer = Renderer.defaultSeqRenderer[HttpMethod]
  }
  case class Allow(methods: HttpMethod*) extends ModeledHeader {
    import Allow.methodsRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ methods
    protected def companion = Allow
  }

  object `Accept-Ranges` extends ModeledCompanion {
    def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more)
    implicit val acceptRangesRenderer = Renderer.defaultSeqRenderer[RangeUnit] // cache
  }
  case class `Accept-Ranges`(acceptRanges: Seq[RangeUnit]) extends ModeledHeader {
    import `Accept-Ranges`.acceptRangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = if (acceptRanges.isEmpty) r ~~ "none" else r ~~ acceptRanges
    protected def companion = `Accept-Ranges`
  }

  object Authorization extends ModeledCompanion
  case class Authorization(credentials: HttpCredentials) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ credentials
    protected def companion = Authorization
  }

  object `Cache-Control` extends ModeledCompanion {
    def apply(first: CacheDirective, more: CacheDirective*): `Cache-Control` = apply(first +: more)
    implicit val directivesRenderer = Renderer.defaultSeqRenderer[CacheDirective] // cache
  }
  case class `Cache-Control`(directives: Seq[CacheDirective]) extends ModeledHeader {
    import `Cache-Control`.directivesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ directives
    protected def companion = `Cache-Control`
  }

  object Connection extends ModeledCompanion {
    def apply(first: String, more: String*): Connection = apply(first +: more)
    implicit val tokensRenderer = Renderer.defaultSeqRenderer[String] // cache
  }
  case class Connection(tokens: Seq[String]) extends ModeledHeader {
    import Connection.tokensRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ tokens
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
    def renderValue[R <: Rendering](r: R): r.type = {
      r ~~ dispositionType
      if (parameters.nonEmpty) parameters foreach { case (k, v) ⇒ r ~~ ';' ~~ ' ' ~~ k ~~ '=' ~~# v }
      r
    }
    protected def companion = `Content-Disposition`
  }

  object `Content-Encoding` extends ModeledCompanion
  case class `Content-Encoding`(encoding: HttpEncoding) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ encoding
    protected def companion = `Content-Encoding`
  }

  object `Content-Length` extends ModeledCompanion
  case class `Content-Length`(length: Long)(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ length
    protected def companion = `Content-Length`
  }

  object `Content-Range` extends ModeledCompanion {
    def apply(contentRange: ContentRange): `Content-Range` = apply(RangeUnit.Bytes, contentRange)
  }
  case class `Content-Range`(rangeUnit: RangeUnit, contentRange: ContentRange) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ rangeUnit ~~ ' ' ~~ contentRange
    protected def companion = `Content-Range`
  }

  object `Content-Type` extends ModeledCompanion
  case class `Content-Type`(contentType: ContentType)(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ contentType
    protected def companion = `Content-Type`
  }

  object Cookie extends ModeledCompanion {
    def apply(first: HttpCookie, more: HttpCookie*): Cookie = apply(first +: more)
    implicit val cookieRenderer: Renderer[HttpCookie] = new Renderer[HttpCookie] {
      def render[R <: Rendering](r: R, c: HttpCookie): r.type = r ~~ c.name ~~ '=' ~~ c.content
    }
    implicit val cookiesRenderer: Renderer[Seq[HttpCookie]] =
      Renderer.seqRenderer(separator = "; ") // cache
  }
  case class Cookie(cookies: Seq[HttpCookie]) extends ModeledHeader {
    import Cookie.cookiesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ cookies
    protected def companion = Cookie
  }

  object Date extends ModeledCompanion
  case class Date(date: DateTime)(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
    protected def companion = Date
  }

  object ETag extends ModeledCompanion {
    def apply(tag: String, weak: Boolean = false): ETag = ETag(EntityTag(tag, weak))
  }
  case class ETag(etag: EntityTag) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ etag
    protected def companion = ETag
  }

  object Expect extends ModeledCompanion {
    def apply(first: String, more: String*): Expect = apply(first +: more)
    implicit val expectationsRenderer = Renderer.defaultSeqRenderer[String] // cache
  }
  case class Expect(expectations: Seq[String]) extends ModeledHeader {
    import Expect.expectationsRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ expectations
    def has100continue = expectations.exists(_ equalsIgnoreCase "100-continue")
    protected def companion = Expect
  }

  object Host extends ModeledCompanion {
    def apply(address: InetSocketAddress): Host = apply(address.getHostName, address.getPort)
    val empty = Host("")
  }
  case class Host(host: String, port: Int = 0) extends ModeledHeader {
    require((port >> 16) == 0, "Illegal port: " + port)
    def isEmpty = host.isEmpty
    def renderValue[R <: Rendering](r: R): r.type = if (port > 0) r ~~ host ~~ ':' ~~ port else r ~~ host
    protected def companion = Host
  }

  object `If-Match` extends ModeledCompanion {
    val `*` = `If-Match`(EntityTagRange.`*`)
    def apply(first: EntityTag, more: EntityTag*): `If-Match` =
      `If-Match`(EntityTagRange(first +: more))
  }
  case class `If-Match`(m: EntityTagRange) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ m
    protected def companion = `If-Match`
  }

  object `If-Modified-Since` extends ModeledCompanion
  case class `If-Modified-Since`(date: DateTime) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
    protected def companion = `If-Modified-Since`
  }

  object `If-None-Match` extends ModeledCompanion {
    val `*` = `If-None-Match`(EntityTagRange.`*`)
    def apply(first: EntityTag, more: EntityTag*): `If-None-Match` =
      `If-None-Match`(EntityTagRange(first +: more))
  }
  case class `If-None-Match`(m: EntityTagRange) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ m
    protected def companion = `If-None-Match`
  }

  object `If-Unmodified-Since` extends ModeledCompanion
  case class `If-Unmodified-Since`(date: DateTime) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
    protected def companion = `If-Unmodified-Since`
  }

  object `Last-Modified` extends ModeledCompanion
  case class `Last-Modified`(date: DateTime) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
    protected def companion = `Last-Modified`
  }

  object Location extends ModeledCompanion
  case class Location(uri: Uri) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ uri
    protected def companion = Location
  }

  object Origin extends ModeledCompanion // TODO: turn argument into repeated parameter for more convenience
  case class Origin(originList: Seq[HttpOrigin]) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ originList
    protected def companion = Origin
  }

  object Range extends ModeledCompanion {
    def apply(first: ByteRange, more: ByteRange*): Range = apply(first +: more)
    def apply(ranges: Seq[ByteRange]): Range = Range(RangeUnit.Bytes, ranges)
    implicit val rangesRenderer = Renderer.defaultSeqRenderer[ByteRange] // cache
  }
  case class Range(rangeUnit: RangeUnit, ranges: Seq[ByteRange]) extends ModeledHeader {
    import Range.rangesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ rangeUnit ~~ '=' ~~ ranges
    protected def companion = Range
  }

  object `Proxy-Authenticate` extends ModeledCompanion {
    def apply(first: HttpChallenge, more: HttpChallenge*): `Proxy-Authenticate` = apply(first +: more)
    implicit val challengesRenderer = Renderer.defaultSeqRenderer[HttpChallenge] // cache
  }
  case class `Proxy-Authenticate`(challenges: Seq[HttpChallenge]) extends ModeledHeader {
    import `Proxy-Authenticate`.challengesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ challenges
    protected def companion = `Proxy-Authenticate`
  }

  object `Proxy-Authorization` extends ModeledCompanion
  case class `Proxy-Authorization`(credentials: HttpCredentials) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ credentials
    protected def companion = `Proxy-Authorization`
  }

  object `Raw-Request-URI` extends ModeledCompanion
  case class `Raw-Request-URI`(uri: String) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ uri
    protected def companion = `Raw-Request-URI`
  }

  object `Remote-Address` extends ModeledCompanion {
    def apply(address: String): `Remote-Address` = apply(RemoteAddress(address))
  }
  case class `Remote-Address`(address: RemoteAddress) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ address
    protected def companion = `Remote-Address`
  }

  object Server extends ModeledCompanion {
    def apply(products: String): Server = apply(ProductVersion.parseMultiple(products))
    def apply(first: ProductVersion, more: ProductVersion*): Server = apply(first +: more)
  }
  case class Server(products: Seq[ProductVersion])(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ products
    protected def companion = Server
  }

  object `Set-Cookie` extends ModeledCompanion
  case class `Set-Cookie`(cookie: HttpCookie) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ cookie
    protected def companion = `Set-Cookie`
  }

  object `Transfer-Encoding` extends ModeledCompanion {
    def apply(first: String, more: String*): `Transfer-Encoding` = apply(first +: more)
    implicit val encodingsRenderer = Renderer.defaultSeqRenderer[String] // cache
  }
  case class `Transfer-Encoding`(encodings: Seq[String])(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
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
    protected def companion = `Transfer-Encoding`
  }

  object `User-Agent` extends ModeledCompanion {
    def apply(products: String): `User-Agent` = apply(ProductVersion.parseMultiple(products))
    def apply(first: ProductVersion, more: ProductVersion*): `User-Agent` = apply(first +: more)
  }
  case class `User-Agent`(products: Seq[ProductVersion])(implicit ev: ProtectedHeaderCreation.Enabled) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ products
    protected def companion = `User-Agent`
  }

  object `WWW-Authenticate` extends ModeledCompanion {
    def apply(first: HttpChallenge, more: HttpChallenge*): `WWW-Authenticate` = apply(first +: more)
    implicit val challengesRenderer = Renderer.defaultSeqRenderer[HttpChallenge] // cache
  }
  case class `WWW-Authenticate`(challenges: Seq[HttpChallenge]) extends ModeledHeader {
    import `WWW-Authenticate`.challengesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ challenges
    protected def companion = `WWW-Authenticate`
  }

  object `X-Forwarded-For` extends ModeledCompanion {
    def apply(first: String, more: String*): `X-Forwarded-For` = apply((first +: more).map(RemoteAddress.apply))
    def apply(first: RemoteAddress, more: RemoteAddress*): `X-Forwarded-For` = apply(first +: more)
    implicit val addressesRenderer = Renderer.defaultSeqRenderer[RemoteAddress]
  }
  case class `X-Forwarded-For`(addresses: Seq[RemoteAddress]) extends ModeledHeader {
    import `X-Forwarded-For`.addressesRenderer
    def renderValue[R <: Rendering](r: R): r.type = r ~~ addresses
    protected def companion = `X-Forwarded-For`
  }

  /**
   * Provides information about the SSL session the message was received over.
   *
   * For non-certificate based cipher suites (e.g., Kerberos), `localCertificates` and `peerCertificates` are both empty lists.
   */
  object `SSL-Session-Info` extends ModeledCompanion
  case class `SSL-Session-Info`(info: SSLSessionInfo) extends ModeledHeader {
    def renderValue[R <: Rendering](r: R): r.type = r ~~ "peer = " ~~ info.peerPrincipal.map { _.toString }.getOrElse("none")
    protected def companion = `SSL-Session-Info`
    override def toString = s"$name($info)"
  }

  case class RawHeader(name: String, value: String) extends HttpHeader {
    val lowercaseName = name.toLowerCase
    def render[R <: Rendering](r: R): r.type = r ~~ name ~~ ':' ~~ ' ' ~~ value
  }
}
