/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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
import scala.reflect.{ classTag, ClassTag }
import HttpHeaders._
import HttpCharsets._

sealed trait HttpMessagePartWrapper {
  def messagePart: HttpMessagePart
  def ack: Option[Any]
}

case class Confirmed(messagePart: HttpMessagePart, sentAck: Any) extends HttpMessagePartWrapper {
  val ack = Some(sentAck)
}

object HttpMessagePartWrapper {
  def unapply(x: HttpMessagePartWrapper): Option[(HttpMessagePart, Option[Any])] = Some((x.messagePart, x.ack))
}

sealed trait HttpMessagePart extends HttpMessagePartWrapper {
  def messagePart = this
  def ack = None
  def withAck(ack: Any) = Confirmed(this, ack)
}

sealed trait HttpRequestPart extends HttpMessagePart

object HttpRequestPart {
  def unapply(wrapper: HttpMessagePartWrapper): Option[(HttpRequestPart, Option[Any])] =
    wrapper.messagePart match {
      case x: HttpRequestPart ⇒ Some((x, wrapper.ack))
      case _                  ⇒ None
    }
}

sealed trait HttpResponsePart extends HttpMessagePart

object HttpResponsePart {
  def unapply(wrapper: HttpMessagePartWrapper): Option[(HttpResponsePart, Option[Any])] =
    wrapper.messagePart match {
      case x: HttpResponsePart ⇒ Some((x, wrapper.ack))
      case _                   ⇒ None
    }
}

sealed trait HttpMessageStart extends HttpMessagePart {
  def message: HttpMessage

  def mapHeaders(f: List[HttpHeader] ⇒ List[HttpHeader]): HttpMessageStart
}

object HttpMessageStart {
  def unapply(x: HttpMessageStart): Option[HttpMessage] = Some(x.message)
}

sealed trait HttpMessageEnd extends HttpMessagePart

sealed abstract class HttpMessage extends HttpMessageStart with HttpMessageEnd {
  type Self <: HttpMessage

  def message: Self
  def isRequest: Boolean
  def isResponse: Boolean

  def headers: List[HttpHeader]
  def entity: HttpEntity
  def protocol: HttpProtocol

  def withHeaders(headers: HttpHeader*): Self = withHeaders(headers.toList)
  def withDefaultHeaders(defaultHeaders: List[HttpHeader]) = {
    @tailrec def patch(remaining: List[HttpHeader], result: List[HttpHeader] = headers): List[HttpHeader] =
      remaining match {
        case h :: rest if result.exists(_.is(h.lowercaseName)) ⇒ patch(rest, result)
        case h :: rest ⇒ patch(rest, h :: result)
        case Nil ⇒ result
      }
    withHeaders(patch(defaultHeaders))
  }
  def withHeaders(headers: List[HttpHeader]): Self
  def withEntity(entity: HttpEntity): Self
  def withHeadersAndEntity(headers: List[HttpHeader], entity: HttpEntity): Self

  /** Returns the start part for this message */
  def chunkedMessageStart: HttpMessageStart

  def mapHeaders(f: List[HttpHeader] ⇒ List[HttpHeader]): Self = withHeaders(f(headers))
  def mapEntity(f: HttpEntity ⇒ HttpEntity): Self = withEntity(f(entity))

  /**
   * The content encoding as specified by the Content-Encoding header. If no Content-Encoding header is present the
   * default value 'identity' is returned.
   */
  def encoding = header[`Content-Encoding`] match {
    case Some(x) ⇒ x.encoding
    case None    ⇒ HttpEncodings.identity
  }

  def header[T <: HttpHeader: ClassTag]: Option[T] = {
    val erasure = classTag[T].runtimeClass
    @tailrec def next(headers: List[HttpHeader]): Option[T] =
      if (headers.isEmpty) None
      else if (erasure.isInstance(headers.head)) Some(headers.head.asInstanceOf[T]) else next(headers.tail)
    next(headers)
  }

  def connectionCloseExpected: Boolean = HttpMessage.connectionCloseExpected(protocol, header[Connection])

  /** Returns the message as if it was sent in chunks */
  def asPartStream(maxChunkSize: Long = Long.MaxValue): Stream[HttpMessagePart] =
    entity match {
      case HttpEntity.Empty ⇒ Stream(chunkedMessageStart, ChunkedMessageEnd)
      case HttpEntity.NonEmpty(ct, data) ⇒
        val start = withHeadersAndEntity(`Content-Type`(ct) :: headers, HttpEntity.Empty).chunkedMessageStart
        val chunks: Stream[HttpMessagePart] = data.toChunkStream(maxChunkSize).map(MessageChunk(_))
        start #:: chunks append Stream(ChunkedMessageEnd)
    }
}

object HttpMessage {
  private[spray] def connectionCloseExpected(protocol: HttpProtocol, connectionHeader: Option[Connection]): Boolean =
    protocol match {
      case HttpProtocols.`HTTP/1.1` ⇒ connectionHeader.isDefined && connectionHeader.get.hasClose
      case HttpProtocols.`HTTP/1.0` ⇒ connectionHeader.isEmpty || !connectionHeader.get.hasKeepAlive
    }
}

/**
 * Immutable HTTP request model.
 */
case class HttpRequest(method: HttpMethod = HttpMethods.GET,
                       uri: Uri = Uri./,
                       headers: List[HttpHeader] = Nil,
                       entity: HttpEntity = HttpEntity.Empty,
                       protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`) extends HttpMessage with HttpRequestPart {
  require(!uri.isEmpty, "An HttpRequest must not have an empty Uri")

  type Self = HttpRequest
  def message = this
  def isRequest = true
  def isResponse = false

  def withEffectiveUri(securedConnection: Boolean, defaultHostHeader: Host = Host.empty): HttpRequest = {
    val hostHeader = header[Host]
    if (uri.isRelative) {
      def fail(detail: String) =
        sys.error("Cannot establish effective request URI of " + this + ", request has a relative URI and " + detail)
      val Host(host, port) = hostHeader match {
        case None                 ⇒ if (defaultHostHeader.isEmpty) fail("is missing a `Host` header") else defaultHostHeader
        case Some(x) if x.isEmpty ⇒ if (defaultHostHeader.isEmpty) fail("an empty `Host` header") else defaultHostHeader
        case Some(x)              ⇒ x
      }
      copy(uri = uri.toEffectiveHttpRequestUri(Uri.Host(host), port, securedConnection))
    } else // http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-5.4
    if (hostHeader.isEmpty || uri.authority.isEmpty && hostHeader.get.isEmpty ||
      hostHeader.get.host.equalsIgnoreCase(uri.authority.host.address) && hostHeader.get.port == uri.authority.port) this
    else sys.error("'Host' header value doesn't match request target authority")
  }

  def acceptedMediaRanges: List[MediaRange] =
    (for {
      Accept(mediaRanges) ← headers
      range ← mediaRanges
    } yield range).sortBy(-_.qValue)

  def acceptedCharsetRanges: List[HttpCharsetRange] =
    (for {
      `Accept-Charset`(charsetRanges) ← headers
      range ← charsetRanges
    } yield range).sortBy(-_.qValue)

  def acceptedEncodingRanges: List[HttpEncodingRange] =
    (for {
      `Accept-Encoding`(encodingRanges) ← headers
      range ← encodingRanges
    } yield range).sortBy(-_.qValue)

  def cookies: List[HttpCookie] = for (`Cookie`(cookies) ← headers; cookie ← cookies) yield cookie

  /**
   * Determines whether the given media-type is accepted by the client.
   */
  def isMediaTypeAccepted(mediaType: MediaType, ranges: List[MediaRange] = acceptedMediaRanges): Boolean =
    qValueForMediaType(mediaType, ranges) > 0f

  /**
   * Returns the q-value that the client (implicitly or explicitly) attaches to the given media-type.
   */
  def qValueForMediaType(mediaType: MediaType, ranges: List[MediaRange] = acceptedMediaRanges): Float =
    ranges match {
      case Nil ⇒ 1.0f // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1
      case x ⇒
        @tailrec def rec(r: List[MediaRange] = x): Float = r match {
          case Nil          ⇒ 0f
          case head :: tail ⇒ if (head.matches(mediaType)) head.qValue else rec(tail)
        }
        rec()
    }

  /**
   * Determines whether the given charset is accepted by the client.
   */
  def isCharsetAccepted(charset: HttpCharset, ranges: List[HttpCharsetRange] = acceptedCharsetRanges): Boolean =
    qValueForCharset(charset, ranges) > 0f

  /**
   * Returns the q-value that the client (implicitly or explicitly) attaches to the given charset.
   */
  def qValueForCharset(charset: HttpCharset, ranges: List[HttpCharsetRange] = acceptedCharsetRanges): Float =
    ranges match {
      case Nil ⇒ 1.0f // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.2
      case x ⇒
        @tailrec def rec(r: List[HttpCharsetRange] = x): Float = r match {
          case Nil          ⇒ if (charset == `ISO-8859-1`) 1f else 0f
          case head :: tail ⇒ if (head.matches(charset)) head.qValue else rec(tail)
        }
        rec()
    }

  /**
   * Determines whether the given encoding is accepted by the client.
   */
  def isEncodingAccepted(encoding: HttpEncoding, ranges: List[HttpEncodingRange] = acceptedEncodingRanges): Boolean =
    qValueForEncoding(encoding, ranges) > 0f

  /**
   * Returns the q-value that the client (implicitly or explicitly) attaches to the given encoding.
   */
  def qValueForEncoding(encoding: HttpEncoding, ranges: List[HttpEncodingRange] = acceptedEncodingRanges): Float =
    ranges match {
      case Nil ⇒ 1.0f // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3
      case x ⇒
        @tailrec def rec(r: List[HttpEncodingRange] = x): Float = r match {
          case Nil          ⇒ 0f
          case head :: tail ⇒ if (head.matches(encoding)) head.qValue else rec(tail)
        }
        rec()
    }

  /**
   * Determines whether one of the given content-types is accepted by the client.
   * If a given ContentType does not define a charset an accepted charset is selected, i.e. the method guarantees
   * that, if a ContentType instance is returned within the option, it will contain a defined charset.
   */
  def acceptableContentType(contentTypes: Seq[ContentType]): Option[ContentType] = {
    val mediaRanges = acceptedMediaRanges // cache for performance
    val charsetRanges = acceptedCharsetRanges // cache for performance

    @tailrec def findBest(ix: Int = 0, result: ContentType = null, maxQ: Float = 0f): Option[ContentType] =
      if (ix < contentTypes.size) {
        val ct = contentTypes(ix)
        val q = qValueForMediaType(ct.mediaType, mediaRanges)
        if (q > maxQ && (ct.noCharsetDefined || isCharsetAccepted(ct.charset, charsetRanges))) findBest(ix + 1, ct, q)
        else findBest(ix + 1, result, maxQ)
      } else Option(result)

    findBest() match {
      case x @ Some(ct) if ct.isCharsetDefined ⇒ x
      case Some(ct) ⇒
        // logic for choosing the charset adapted from http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.2
        def withCharset(cs: HttpCharset) = Some(ContentType(ct.mediaType, cs))
        if (qValueForCharset(`UTF-8`, charsetRanges) == 1f) withCharset(`UTF-8`)
        else charsetRanges match { // ranges are sorted by descending q-value
          case (HttpCharsetRange.One(cs, qValue)) :: _ ⇒
            if (qValue == 1f) withCharset(cs)
            else if (qValueForCharset(`ISO-8859-1`, charsetRanges) == 1f) withCharset(`ISO-8859-1`)
            else if (qValue > 0f) withCharset(cs)
            else None
          case _ ⇒ None
        }
      case None ⇒ None
    }
  }

  def canBeRetried = method.isIdempotent
  def withHeaders(headers: List[HttpHeader]) = if (headers eq this.headers) this else copy(headers = headers)
  def withEntity(entity: HttpEntity) = if (entity eq this.entity) this else copy(entity = entity)
  def withHeadersAndEntity(headers: List[HttpHeader], entity: HttpEntity) =
    if ((headers eq this.headers) && (entity eq this.entity)) this else copy(headers = headers, entity = entity)

  def chunkedMessageStart: ChunkedRequestStart = ChunkedRequestStart(this)
}

/**
 * Immutable HTTP response model.
 */
case class HttpResponse(status: StatusCode = StatusCodes.OK,
                        entity: HttpEntity = HttpEntity.Empty,
                        headers: List[HttpHeader] = Nil,
                        protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`) extends HttpMessage with HttpResponsePart {
  type Self = HttpResponse

  def message = this
  def isRequest = false
  def isResponse = true

  def withHeaders(headers: List[HttpHeader]) = if (headers eq this.headers) this else copy(headers = headers)
  def withEntity(entity: HttpEntity) = if (entity eq this.entity) this else copy(entity = entity)
  def withHeadersAndEntity(headers: List[HttpHeader], entity: HttpEntity) =
    if ((headers eq this.headers) && (entity eq this.entity)) this else copy(headers = headers, entity = entity)

  def chunkedMessageStart: ChunkedResponseStart = ChunkedResponseStart(this)
}

/**
 * Instance of this class represent the individual chunks of a chunked HTTP message (request or response).
 */
case class MessageChunk(data: HttpData.NonEmpty, extension: String) extends HttpRequestPart with HttpResponsePart

object MessageChunk {
  import HttpCharsets._
  def apply(body: String): MessageChunk =
    apply(body, "")
  def apply(body: String, charset: HttpCharset): MessageChunk =
    apply(body, charset, "")
  def apply(body: String, extension: String): MessageChunk =
    apply(body, `UTF-8`, extension)
  def apply(body: String, charset: HttpCharset, extension: String): MessageChunk =
    apply(HttpData(body, charset), extension)
  def apply(bytes: Array[Byte]): MessageChunk =
    apply(HttpData(bytes))
  def apply(data: HttpData): MessageChunk =
    apply(data, "")
  def apply(data: HttpData, extension: String): MessageChunk =
    data match {
      case x: HttpData.NonEmpty ⇒ new MessageChunk(x, extension)
      case _                    ⇒ throw new IllegalArgumentException("Cannot create MessageChunk with empty data")
    }
}

case class ChunkedRequestStart(request: HttpRequest) extends HttpMessageStart with HttpRequestPart {
  def message = request

  def mapHeaders(f: List[HttpHeader] ⇒ List[HttpHeader]): ChunkedRequestStart =
    ChunkedRequestStart(request mapHeaders f)
}

case class ChunkedResponseStart(response: HttpResponse) extends HttpMessageStart with HttpResponsePart {
  def message = response

  def mapHeaders(f: List[HttpHeader] ⇒ List[HttpHeader]): ChunkedResponseStart =
    ChunkedResponseStart(response mapHeaders f)
}

object ChunkedMessageEnd extends ChunkedMessageEnd("", Nil)
case class ChunkedMessageEnd(extension: String = "",
                             trailer: List[HttpHeader] = Nil) extends HttpRequestPart with HttpResponsePart with HttpMessageEnd {
  if (!trailer.isEmpty) {
    require(trailer.forall(_.isNot("content-length")), "Content-Length header is not allowed in trailer")
    require(trailer.forall(_.isNot("transfer-encoding")), "Transfer-Encoding header is not allowed in trailer")
    require(trailer.forall(_.isNot("trailer")), "Trailer header is not allowed in trailer")
  }
}
