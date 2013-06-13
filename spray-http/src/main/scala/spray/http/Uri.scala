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

import java.lang.{ StringBuilder ⇒ JStringBuilder }
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.{ mutable, LinearSeqOptimized }
import scala.collection.immutable.LinearSeq
import spray.http.parser.{ ParserInput, UriParser }
import UriParser._
import Uri._

/**
 * An immutable model of an internet URI as defined by http://tools.ietf.org/html/rfc3986.
 * All members of this class represent the *decoded* URI elements (i.e. without percent-encoding).
 */
sealed abstract case class Uri(scheme: String, authority: Authority, path: Path, query: Query,
                               fragment: Option[String]) extends ToStringRenderable {

  def isAbsolute: Boolean = !isRelative
  def isRelative: Boolean = scheme.isEmpty
  def isEmpty: Boolean

  def inspect: String = s"Uri(scheme=$scheme, authority=$authority, path=$path, query=$query, fragment=$fragment)"

  /**
   * Returns a copy of this Uri with the given components.
   */
  def copy(scheme: String = scheme, authority: Authority = authority, path: Path = path,
           query: Query = query, fragment: Option[String] = fragment): Uri =
    Uri(scheme, authority, path, query, fragment)

  /**
   * Returns a new absolute Uri that is the result of the resolution process defined by
   * http://tools.ietf.org/html/rfc3986#section-5.2.2
   * The given base Uri must be absolute.
   */
  def resolvedAgainst(base: Uri): Uri =
    resolve(scheme, authority.userinfo, authority.host, authority.port, path, query, fragment, base)

  def render[R <: Rendering](r: R): r.type = render(r, UTF8)

  /**
   * Renders this Uri into the given Renderer as defined by http://tools.ietf.org/html/rfc3986.
   * All Uri components are encoded and joined as required by the spec. The given charset is used to
   * produce percent-encoded representations of potentially existing non-ASCII characters in the
   * different components.
   */
  def render[R <: Rendering](r: R, charset: Charset): r.type = {
    renderWithoutFragment(r, charset)
    if (fragment.isDefined) encode(r ~~ '#', fragment.get, charset, QUERY_FRAGMENT_CHAR)
    r
  }

  /**
   * Renders this Uri (without the fragment component) into the given Renderer as defined by
   * http://tools.ietf.org/html/rfc3986.
   * All Uri components are encoded and joined as required by the spec. The given charset is used to
   * produce percent-encoded representations of potentially existing non-ASCII characters in the
   * different components.
   */
  def renderWithoutFragment[R <: Rendering](r: R, charset: Charset): r.type = {
    if (isAbsolute) r ~~ scheme ~~ ':'
    authority.render(r, scheme, charset)
    path.render(r, charset, encodeFirstSegmentColons = isRelative)
    if (!query.isEmpty) query.render(r ~~ '?', charset)
    r
  }

  /**
   * Converts this URI to an "effective HTTP request URI" as defined by
   * http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-5.5.
   */
  def toEffectiveHttpRequestUri(securedConnection: Boolean, hostHeaderHost: Host, hostHeaderPort: Int,
                                defaultAuthority: Authority = Authority.Empty): Uri =
    effectiveHttpRequestUri(scheme, authority.host, authority.port, path, query, fragment, securedConnection,
      hostHeaderHost, hostHeaderPort, defaultAuthority)
}

object Uri {
  object Empty extends Uri("", Authority.Empty, Path.Empty, Query.Empty, None) {
    def isEmpty = true
  }

  val / : Uri = "/"

  /**
   * Parses a valid URI string into a normalized URI reference as defined
   * by http://tools.ietf.org/html/rfc3986#section-4.1.
   * Percent-encoded octets are UTF-8 decoded.
   * Accepts unencoded visible 7-bit ASCII characters in addition to the RFC.
   * If the given string is not a valid URI the method throws an `IllegalUriException`.
   */
  implicit def apply(input: String): Uri = apply(input: ParserInput, UTF8, Uri.ParsingMode.Relaxed)

  /**
   * Parses a valid URI string into a normalized URI reference as defined
   * by http://tools.ietf.org/html/rfc3986#section-4.1.
   * Percent-encoded octets are decoded using the given charset (where specified by the RFC).
   * Accepts unencoded visible 7-bit ASCII characters in addition to the rfc.
   * If the given string is not a valid URI the method throws an `IllegalUriException`.
   */
  def apply(input: ParserInput): Uri = apply(input, UTF8, Uri.ParsingMode.Relaxed)

  /**
   * Parses a valid URI string into a normalized URI reference as defined
   * by http://tools.ietf.org/html/rfc3986#section-4.1.
   * Percent-encoded octets are decoded using the given charset (where specified by the RFC).
   * If strict is `false`, accepts unencoded visible 7-bit ASCII characters in addition to the RFC.
   * If the given string is not a valid URI the method throws an `IllegalUriException`.
   */
  def apply(input: ParserInput, mode: Uri.ParsingMode): Uri = apply(input, UTF8, mode)

  /**
   * Parses a valid URI string into a normalized URI reference as defined
   * by http://tools.ietf.org/html/rfc3986#section-4.1.
   * Percent-encoded octets are decoded using the given charset (where specified by the RFC).
   * If strict is `false`, accepts unencoded visible 7-bit ASCII characters in addition to the RFC.
   * If the given string is not a valid URI the method throws an `IllegalUriException`.
   */
  def apply(input: ParserInput, charset: Charset, mode: Uri.ParsingMode): Uri =
    new UriParser(input, charset, mode).parseReference()

  /**
   * Creates a new Uri instance from the given components.
   * All components are verified and normalized.
   * If the given combination of components does not constitute a valid URI as defined by
   * http://tools.ietf.org/html/rfc3986 the method throws an `IllegalUriException`.
   */
  def apply(scheme: String = "", authority: Authority = Authority.Empty, path: Path = Path.Empty,
            query: Query = Query.Empty, fragment: Option[String] = None): Uri = {
    val p = verifyPath(path, scheme, authority.host)
    Impl(
      scheme = normalizeScheme(scheme),
      authority = authority.normalizedFor(scheme),
      path = if (scheme.isEmpty) p else collapseDotSegments(p),
      query = query,
      fragment = fragment)
  }

  /**
   * Creates a new Uri instance from the given components.
   * All components are verified and normalized.
   * If the given combination of components does not constitute a valid URI as defined by
   * http://tools.ietf.org/html/rfc3986 the method throws an `IllegalUriException`.
   */
  def from(scheme: String = "", userinfo: String = "", host: String = "", port: Int = 0, path: String = "",
           query: Query = Query.Empty, fragment: Option[String] = None,
           mode: Uri.ParsingMode = Uri.ParsingMode.Relaxed): Uri =
    apply(scheme, Authority(Host(host, mode), normalizePort(port, scheme), userinfo), Path(path), query, fragment)

  /**
   * Parses a string into a normalized absolute URI as defined by http://tools.ietf.org/html/rfc3986#section-4.3.
   * Percent-encoded octets are decoded using the given charset (where specified by the RFC).
   * If strict is `false`, accepts unencoded visible 7-bit ASCII characters in addition to the RFC.
   * If the given string is not a valid URI the method throws an `IllegalUriException`.
   */
  def parseAbsolute(input: ParserInput, charset: Charset = UTF8, mode: Uri.ParsingMode = Uri.ParsingMode.Relaxed): Uri =
    new UriParser(input, charset, mode).parseAbsolute()

  /**
   * Parses a string into a normalized URI reference that is immediately resolved against the given base URI as
   * defined by http://tools.ietf.org/html/rfc3986#section-5.2.
   * Note that the given base Uri must be absolute (i.e. define a scheme).
   * Percent-encoded octets are decoded using the given charset (where specified by the RFC).
   * If strict is `false`, accepts unencoded visible 7-bit ASCII characters in addition to the RFC.
   * If the given string is not a valid URI the method throws an `IllegalUriException`.
   */
  def parseAndResolve(string: ParserInput, base: Uri, charset: Charset = UTF8,
                      mode: Uri.ParsingMode = Uri.ParsingMode.Relaxed): Uri =
    new UriParser(string, charset, mode).parseAndResolveReference(base)

  /**
   * Parses the given string into an HTTP request target URI as defined by
   * http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-5.3.
   * If strict is `false`, accepts unencoded visible 7-bit ASCII characters in addition to the RFC.
   * If the given string is not a valid URI the method throws an `IllegalUriException`.
   */
  def parseHttpRequestTarget(requestTarget: ParserInput, charset: Charset = UTF8,
                             mode: Uri.ParsingMode = Uri.ParsingMode.Relaxed): Uri =
    new UriParser(requestTarget, charset, mode).parseHttpRequestTarget()

  /**
   * Normalizes the given URI string by performing the following normalizations:
   * - the `scheme` and `host` components are converted to lowercase
   * - a potentially existing `port` component is removed if it matches one of the defined default ports for the scheme
   * - percent-encoded octets are decoded if allowed, otherwise they are converted to uppercase hex notation
   * - `.` and `..` path segments are resolved as far as possible
   *
   * If strict is `false`, accepts unencoded visible 7-bit ASCII characters in addition to the RFC.
   * If the given string is not a valid URI the method throws an `IllegalUriException`.
   */
  def normalize(uri: ParserInput, charset: Charset = UTF8, mode: Uri.ParsingMode = Uri.ParsingMode.Relaxed): String =
    apply(uri, charset, mode).render(new StringRendering, charset).get

  /**
   * Converts a set of URI components to an "effective HTTP request URI" as defined by
   * http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-5.5.
   */
  def effectiveHttpRequestUri(scheme: String, host: Host, port: Int, path: Path, query: Query, fragment: Option[String],
                              securedConnection: Boolean, hostHeaderHost: Host, hostHeaderPort: Int,
                              defaultAuthority: Authority = Authority.Empty): Uri = {
    var _scheme = scheme
    var _host = host
    var _port = port
    if (_scheme.isEmpty) {
      _scheme = if (securedConnection) "https" else "http"
      if (_host.isEmpty) {
        if (hostHeaderHost.isEmpty) {
          _host = defaultAuthority.host
          _port = defaultAuthority.port
        } else {
          _host = hostHeaderHost
          _port = hostHeaderPort
        }
      }
    }
    Impl(_scheme, "", _host, _port, collapseDotSegments(path), query, fragment)
  }

  case class Authority(host: Host, port: Int = 0, userinfo: String = "") extends ToStringRenderable {
    def isEmpty = host.isEmpty
    def render[R <: Rendering](r: R): r.type = render(r, "", UTF8)
    def render[R <: Rendering](r: R, scheme: String, charset: Charset): r.type =
      if (isEmpty) r else {
        r ~~ '/' ~~ '/'
        if (!userinfo.isEmpty) encode(r, userinfo, charset, UNRESERVED | SUB_DELIM | COLON) ~~ '@'
        r ~~ host
        if (port != 0) normalizePort(port, scheme) match {
          case 0 ⇒ r
          case x ⇒ r ~~ ':' ~~ port
        }
        else r
      }
    def normalizedFor(scheme: String): Authority = {
      val normalizedPort = normalizePort(port, scheme)
      if (normalizedPort == port) this else copy(port = normalizedPort)
    }
  }
  object Authority {
    val Empty = Authority(Host.Empty)
  }

  sealed abstract class Host extends Renderable {
    def address: String
    def isEmpty: Boolean
    def toOption: Option[NonEmptyHost]
  }
  object Host {
    case object Empty extends Host {
      def address: String = ""
      def isEmpty = true
      def toOption = None
      def render[R <: Rendering](r: R): r.type = r
    }
    def apply(string: String, mode: Uri.ParsingMode = Uri.ParsingMode.Relaxed): Host =
      if (!string.isEmpty) {
        val parser = new UriParser(string, UTF8, mode)
        import parser._
        complete("URI host", host)
        _host
      } else Empty
  }
  sealed abstract class NonEmptyHost extends Host with ToStringRenderable {
    def isEmpty = false
    def toOption = Some(this)
  }
  case class IPv4Host(address: String) extends NonEmptyHost {
    require(!address.isEmpty, "address must not be empty")
    def render[R <: Rendering](r: R): r.type = r ~~ address
  }
  case class IPv6Host(address: String) extends NonEmptyHost {
    require(!address.isEmpty, "address must not be empty")
    def render[R <: Rendering](r: R): r.type = r ~~ '[' ~~ address ~~ ']'
  }
  case class NamedHost(address: String) extends NonEmptyHost {
    def render[R <: Rendering](r: R): r.type = encode(r, address, UTF8, UNRESERVED | SUB_DELIM)
  }

  sealed abstract class Path extends ToStringRenderable {
    type Head
    def isEmpty: Boolean
    def startsWithSlash: Boolean
    def startsWithSegment: Boolean
    def head: Head
    def tail: Path
    def length: Int
    def charCount: Int // count of decoded (!) chars, i.e. the ones contained directly in this high-level model
    def render[R <: Rendering](r: R): r.type = render(r, UTF8, encodeFirstSegmentColons = false)
    def render[R <: Rendering](r: R, charset: Charset, encodeFirstSegmentColons: Boolean): r.type
    def ::(c: Char): Path = { require(c == '/'); Path.Slash(this) }
    def ::(segment: String): Path
    def +(pathString: String): Path = this ++ Path(pathString)
    def ++(suffix: Path): Path
    def reverse: Path = reverseAndPrependTo(Path.Empty)
    def reverseAndPrependTo(prefix: Path): Path
    def /(segment: String): Path = this ++ Path.Slash(segment :: Path.Empty)
    def startsWith(that: Path): Boolean
    def dropChars(count: Int): Path
  }
  object Path {
    val SingleSlash = Slash(Empty)
    def / : Path = SingleSlash
    def /(path: Path): Path = Slash(path)
    def /(segment: String): Path = Slash(segment :: Empty)
    def apply(string: String, charset: Charset = UTF8): Path = {
      @tailrec def build(path: Path = Empty, ix: Int = string.length - 1, segmentEnd: Int = 0): Path =
        if (ix >= 0)
          if (string.charAt(ix) == '/')
            if (segmentEnd == 0) build(Slash(path), ix - 1)
            else build(Slash(decode(string.substring(ix + 1, segmentEnd), charset) :: path), ix - 1)
          else if (segmentEnd == 0) build(path, ix - 1, ix + 1)
          else build(path, ix - 1, segmentEnd)
        else if (segmentEnd == 0) path else decode(string.substring(0, segmentEnd), charset) :: path
      build()
    }
    def unapply(path: Path): Option[String] = Some(path.toString)
    def unapply(uri: Uri): Option[String] = unapply(uri.path)
    sealed abstract class SlashOrEmpty extends Path {
      def startsWithSegment = false
    }
    case object Empty extends SlashOrEmpty {
      type Head = Nothing
      def isEmpty = true
      def startsWithSlash = false
      def head: Head = throw new NoSuchElementException("head of empty path")
      def tail: Path = throw new UnsupportedOperationException("tail of empty path")
      def length = 0
      def charCount = 0
      def render[R <: Rendering](r: R, charset: Charset, encodeFirstSegmentColons: Boolean): r.type = r
      def ::(segment: String) = if (segment.isEmpty) this else Segment(segment, this)
      def ++(suffix: Path) = suffix
      def reverseAndPrependTo(prefix: Path) = prefix
      def startsWith(that: Path): Boolean = that.isEmpty
      def dropChars(count: Int) = this
    }
    case class Slash(tail: Path) extends SlashOrEmpty {
      type Head = Char
      def head = '/'
      def startsWithSlash = true
      def isEmpty = false
      def length: Int = tail.length + 1
      def charCount: Int = tail.charCount + 1
      def render[R <: Rendering](r: R, charset: Charset, encodeFirstSegmentColons: Boolean): r.type =
        tail.render(r ~~ '/', charset, encodeFirstSegmentColons = false)
      def ::(segment: String) = if (segment.isEmpty) this else Segment(segment, this)
      def ++(suffix: Path) = Slash(tail ++ suffix)
      def reverseAndPrependTo(prefix: Path) = tail.reverseAndPrependTo(Slash(prefix))
      def startsWith(that: Path): Boolean = that.isEmpty || that.startsWithSlash && tail.startsWith(that.tail)
      def dropChars(count: Int): Path = if (count < 1) this else tail.dropChars(count - 1)
    }
    case class Segment(head: String, tail: SlashOrEmpty) extends Path {
      if (head.isEmpty) throw new IllegalArgumentException("Path segment must not be empty")
      type Head = String
      def isEmpty = false
      def startsWithSlash = false
      def startsWithSegment = true
      def length: Int = tail.length + 1
      def charCount: Int = head.length + tail.charCount
      def render[R <: Rendering](r: R, charset: Charset, encodeFirstSegmentColons: Boolean): r.type = {
        val keep = if (encodeFirstSegmentColons) PATH_SEGMENT_CHAR & ~COLON else PATH_SEGMENT_CHAR
        tail.render(encode(r, head, charset, keep), charset, encodeFirstSegmentColons = false)
      }
      def ::(segment: String) = if (segment.isEmpty) this else Segment(segment + head, tail)
      def ++(suffix: Path) = head :: (tail ++ suffix)
      def reverseAndPrependTo(prefix: Path): Path = tail.reverseAndPrependTo(head :: prefix)
      def startsWith(that: Path): Boolean = that match {
        case Segment(`head`, t) ⇒ tail.startsWith(t)
        case Segment(h, Empty)  ⇒ head.startsWith(h)
        case x                  ⇒ x.isEmpty
      }
      def dropChars(count: Int): Path =
        if (count < 1) this
        else if (count >= head.length) tail.dropChars(count - head.length)
        else head.substring(count) :: tail
    }
    object ~ {
      def unapply(cons: Segment): Option[(String, Path)] = Some((cons.head, cons.tail))
      def unapply(cons: Slash): Option[(Char, Path)] = Some(('/', cons.tail))
    }
  }

  sealed abstract class Query extends LinearSeq[(String, String)] with LinearSeqOptimized[(String, String), Query]
      with Renderable {
    def key: String
    def value: String
    def +:(kvp: (String, String)) = Query.Cons(kvp._1, kvp._2, this)
    def get(key: String): Option[String] = {
      @tailrec def g(q: Query): Option[String] = if (q.isEmpty) None else if (q.key == key) Some(q.value) else g(q.tail)
      g(this)
    }
    def getOrElse(key: String, default: ⇒ String): String = {
      @tailrec def g(q: Query): String = if (q.isEmpty) default else if (q.key == key) q.value else g(q.tail)
      g(this)
    }
    def getAll(key: String): List[String] = {
      @tailrec def fetch(q: Query, result: List[String] = Nil): List[String] =
        if (q.isEmpty) result else fetch(q.tail, if (q.key == key) q.value :: result else result)
      fetch(this)
    }
    def toMultiMap: Map[String, List[String]] = {
      @tailrec def append(map: Map[String, List[String]], q: Query): Map[String, List[String]] =
        if (q.isEmpty) map else append(map.updated(q.key, q.value :: map.getOrElse(q.key, Nil)), q.tail)
      append(Map.empty, this)
    }
    def render[R <: Rendering](r: R): r.type = render(r, UTF8)
    def render[R <: Rendering](r: R, charset: Charset): r.type = {
      def enc(r: Rendering, s: String): r.type =
        encode(r, s, charset, QUERY_FRAGMENT_CHAR & ~(AMP | EQUAL | PLUS), replaceSpaces = true)
      @tailrec def append(q: Query): r.type =
        if (!q.isEmpty) {
          if (q ne this) r ~~ '&'
          enc(r, q.key)
          if (!q.value.isEmpty) enc(r ~~ '=', q.value)
          append(q.tail)
        } else r
      append(this)
    }
    override def newBuilder: mutable.Builder[(String, String), Query] = Query.newBuilder
  }
  object Query {
    /**
     * Parses the given String into a Query instance.
     * Note that this method will never return Query.Empty, even for the empty String.
     * Empty strings will be parsed to `("", "") +: Query.Empty`
     * If you want to allow for Query.Empty creation use the apply overload taking an `Option[String`.
     */
    def apply(string: String, mode: Uri.ParsingMode = Uri.ParsingMode.Relaxed): Query = {
      val parser = new UriParser(string, UTF8, mode)
      import parser._
      complete("Query", query)
      _query
    }
    def apply(input: Option[String]): Query = apply(input, Uri.ParsingMode.Relaxed)
    def apply(input: Option[String], mode: Uri.ParsingMode): Query = input match {
      case None         ⇒ Query.Empty
      case Some(string) ⇒ apply(string, mode)
    }
    def apply(kvp: (String, String)*): Query =
      kvp.foldRight(Query.Empty: Query) { case ((key, value), acc) ⇒ Cons(key, value, acc) }
    def apply(map: Map[String, String]): Query = apply(map.toSeq: _*)

    def newBuilder: mutable.Builder[(String, String), Query] = new mutable.Builder[(String, String), Query] {
      val b = mutable.ArrayBuffer.newBuilder[(String, String)]
      def +=(elem: (String, String)): this.type = { b += elem; this }
      def clear() = b.clear()
      def result() = apply(b.result(): _*)
    }

    case object Empty extends Query {
      def key = throw new NoSuchElementException("key of empty path")
      def value = throw new NoSuchElementException("value of empty path")
      override def isEmpty = true
      override def head = throw new NoSuchElementException("head of empty list")
      override def tail = throw new UnsupportedOperationException("tail of empty query")
    }
    case class Cons(key: String, value: String, override val tail: Query) extends Query with ToStringRenderable {
      override def isEmpty = false
      override def head = (key, value)
    }
  }

  val defaultPorts: Map[String, Int] =
    Map("ftp" -> 21, "ssh" -> 22, "telnet" -> 23, "smtp" -> 25, "domain" -> 53, "tftp" -> 69, "http" -> 80,
      "pop3" -> 110, "nntp" -> 119, "imap" -> 143, "snmp" -> 161, "ldap" -> 389, "https" -> 443, "imaps" -> 993,
      "nfs" -> 2049).withDefaultValue(-1)

  sealed trait ParsingMode

  object ParsingMode {
    case object Strict extends ParsingMode
    case object Relaxed extends ParsingMode
    case object RelaxedWithRawQuery extends ParsingMode

    def apply(string: String): ParsingMode =
      string match {
        case "strict"                 ⇒ Strict
        case "relaxed"                ⇒ Relaxed
        case "relaxed-with-raw-query" ⇒ RelaxedWithRawQuery
        case x                        ⇒ throw new IllegalArgumentException(x + " is not a legal UriParsingMode")
      }
  }

  /////////////////////////////////// PRIVATE //////////////////////////////////////////

  // http://tools.ietf.org/html/rfc3986#section-5.2.2
  private[http] def resolve(scheme: String, userinfo: String, host: Host, port: Int, path: Path, query: Query,
                            fragment: Option[String], base: Uri): Uri = {
    require(base.isAbsolute, "Resolution base Uri must be absolute")
    if (scheme.isEmpty)
      if (host.isEmpty)
        if (path.isEmpty) {
          val q = if (query.isEmpty) base.query else query
          Impl(base.scheme, base.authority, base.path, q, fragment)
        } else {
          // http://tools.ietf.org/html/rfc3986#section-5.2.3
          def mergePaths(base: Uri, path: Path): Path =
            if (!base.authority.isEmpty && path.isEmpty) Path.Slash(path)
            else {
              import Path._
              def replaceLastSegment(p: Path, replacement: Path): Path = p match {
                case Path.Empty | Segment(_, Path.Empty) ⇒ replacement
                case Segment(string, tail)               ⇒ string :: replaceLastSegment(tail, replacement)
                case Slash(tail)                         ⇒ Slash(replaceLastSegment(tail, replacement))
              }
              replaceLastSegment(base.path, path)
            }
          val p = if (path.startsWithSlash) path else mergePaths(base, path)
          Impl(base.scheme, base.authority, collapseDotSegments(p), query, fragment)
        }
      else Impl(base.scheme, userinfo, host, port, collapseDotSegments(path), query, fragment)
    else Impl(scheme, userinfo, host, port, collapseDotSegments(path), query, fragment)
  }

  private[http] def encode(r: Rendering, string: String, charset: Charset, keep: Int,
                           replaceSpaces: Boolean = false): r.type = {
    @tailrec def rec(ix: Int = 0): r.type = {
      def appendEncoded(byte: Byte): Unit = r ~~ '%' ~~ hexDigit(byte >>> 4) ~~ hexDigit(byte)
      if (ix < string.length) {
        string.charAt(ix) match {
          case c if is(c, keep)     ⇒ r ~~ c
          case ' ' if replaceSpaces ⇒ r ~~ '+'
          case c if c <= 127        ⇒ appendEncoded(c.toByte)
          case c                    ⇒ c.toString.getBytes(charset).foreach(appendEncoded)
        }
        rec(ix + 1)
      } else r
    }
    rec()
  }

  private[http] def decode(string: String, charset: Charset): String = {
    val ix = string.indexOf('%')
    if (ix >= 0) decode(string, charset, ix)() else string
  }

  @tailrec
  private[http] def decode(string: String, charset: Charset, ix: Int)(sb: JStringBuilder = new JStringBuilder(string.length).append(string, 0, ix)): String =
    if (ix < string.length) string.charAt(ix) match {
      case '%' ⇒
        def intValueOfHexWord(i: Int) = {
          def intValueOfHexChar(j: Int) = {
            val c = string.charAt(j)
            if (is(c, HEX_DIGIT)) hexValue(c)
            else throw new IllegalArgumentException("Illegal percent-encoding at pos " + j)
          }
          intValueOfHexChar(i) * 16 + intValueOfHexChar(i + 1)
        }

        var lastPercentSignIndexPlus3 = ix + 3
        while (lastPercentSignIndexPlus3 < string.length && string.charAt(lastPercentSignIndexPlus3) == '%')
          lastPercentSignIndexPlus3 += 3
        val bytesCount = (lastPercentSignIndexPlus3 - ix) / 3
        val bytes = new Array[Byte](bytesCount)

        @tailrec def decodeBytes(i: Int = 0, oredBytes: Int = 0): Int =
          if (i < bytesCount) {
            val byte = intValueOfHexWord(ix + 3 * i + 1)
            bytes(i) = byte.toByte
            decodeBytes(i + 1, oredBytes | byte)
          } else oredBytes

        if ((decodeBytes() >> 7) != 0) { // if non-ASCII chars are present we need to involve the charset for decoding
          sb.append(new String(bytes, charset))
        } else {
          @tailrec def appendBytes(i: Int = 0): Unit =
            if (i < bytesCount) { sb.append(bytes(i).toChar); appendBytes(i + 1) }
          appendBytes()
        }
        decode(string, charset, lastPercentSignIndexPlus3)(sb)

      case x ⇒ decode(string, charset, ix + 1)(sb.append(x))
    }
    else sb.toString

  private[http] def normalizeScheme(scheme: String): String = {
    @tailrec def verify(ix: Int = scheme.length - 1, allowed: Int = ALPHA, allLower: Boolean = true): Int =
      if (ix >= 0) {
        val c = scheme.charAt(ix)
        if (is(c, allowed)) verify(ix - 1, ALPHA | DIGIT | PLUS | DASH | DOT, allLower && !is(c, UPPER_ALPHA)) else ix
      } else if (allLower) -1 else -2
    verify() match {
      case -2 ⇒ scheme.toLowerCase
      case -1 ⇒ scheme
      case ix ⇒ fail(s"Invalid URI scheme, unexpected character at pos $ix ('${scheme.charAt(ix)}')")
    }
  }

  private[http] def normalizePort(port: Int, scheme: String): Int =
    if ((port >> 16) == 0)
      if (port != 0 && defaultPorts(scheme) == port) 0 else port
    else fail("Invalid port " + port)

  private[http] def verifyPath(path: Path, scheme: String, host: Host): Path = {
    if (host.isEmpty) {
      if (path.startsWithSlash && path.tail.startsWithSlash)
        fail("""The path of an URI without authority must not begin with "//"""")
    } else if (path.startsWithSegment)
      fail("The path of an URI containing an authority must either be empty or start with a '/' (slash) character")
    path
  }

  private[http] def collapseDotSegments(path: Path): Path = {
    @tailrec def hasDotOrDotDotSegment(p: Path): Boolean = p match {
      case Path.Empty ⇒ false
      case Path.Segment(".", _) | Path.Segment("..", _) ⇒ true
      case _ ⇒ hasDotOrDotDotSegment(p.tail)
    }
    // http://tools.ietf.org/html/rfc3986#section-5.2.4
    @tailrec def process(input: Path, output: Path = Path.Empty): Path = {
      import Path._
      input match {
        case Path.Empty                       ⇒ output.reverse
        case Segment("." | "..", Slash(tail)) ⇒ process(tail, output)
        case Slash(Segment(".", tail))        ⇒ process(if (tail.isEmpty) Path./ else tail, output)
        case Slash(Segment("..", tail)) ⇒ process(
          input = if (tail.isEmpty) Path./ else tail,
          output =
            if (output.startsWithSegment)
              if (output.tail.startsWithSlash) output.tail.tail else tail
            else output)
        case Segment("." | "..", tail) ⇒ process(tail, output)
        case Slash(tail)               ⇒ process(tail, Slash(output))
        case Segment(string, tail)     ⇒ process(tail, string :: output)
      }
    }
    if (hasDotOrDotDotSegment(path)) process(path) else path
  }

  private[http] def fail(summary: String, detail: String = "") =
    throw new IllegalUriException(ErrorInfo(summary, detail))

  private[http] class Impl private (scheme: String, authority: Authority, path: Path, query: Query,
                                    fragment: Option[String]) extends Uri(scheme, authority, path, query, fragment) {
    def isEmpty = false
  }

  private[http] object Impl {
    def apply(scheme: String, authority: Authority, path: Path, query: Query, fragment: Option[String]): Uri =
      if (path.isEmpty && scheme.isEmpty && authority.isEmpty && query.isEmpty && fragment.isEmpty) Empty
      else new Impl(scheme, authority, path, query, fragment)
    def apply(scheme: String, userinfo: String, host: Host, port: Int, path: Path, query: Query,
              fragment: Option[String]): Uri =
      apply(scheme, Authority(host, normalizePort(port, scheme), userinfo), path, query, fragment)
  }
}