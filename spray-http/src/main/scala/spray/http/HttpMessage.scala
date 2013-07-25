/*
 * Copyright (C) 2011-2013 spray.io
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

import java.util
import java.nio.charset.Charset
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
  def withHeaders(headers: List[HttpHeader]): Self
  def withEntity(entity: HttpEntity): Self
  def withHeadersAndEntity(headers: List[HttpHeader], entity: HttpEntity): Self

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
}

/**
 * Immutable HTTP request model.
 */
case class HttpRequest(method: HttpMethod = HttpMethods.GET,
                       uri: Uri = Uri./,
                       headers: List[HttpHeader] = Nil,
                       entity: HttpEntity = EmptyEntity,
                       protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`) extends HttpMessage with HttpRequestPart {
  require(!uri.isEmpty, "An HttpRequest must not have an empty Uri")

  type Self = HttpRequest
  def message = this
  def isRequest = true
  def isResponse = false

  def withEffectiveUri(securedConnection: Boolean, defaultHostHeader: Host = Host.empty): HttpRequest = {
    val hostHeader = header[Host]
    if (uri.isRelative) {
      val Host(host, port) = hostHeader match {
        case None ⇒
          if (defaultHostHeader.isEmpty)
            sys.error("Cannot establish effective request URI, request has a relative URI and is missing a `Host` header")
          else defaultHostHeader
        case Some(x) if x.isEmpty ⇒
          if (defaultHostHeader.isEmpty)
            sys.error("Cannot establish effective request URI, request has a relative URI and an empty `Host` header")
          else defaultHostHeader
        case Some(x) ⇒ x
      }
      copy(uri = uri.toEffectiveHttpRequestUri(securedConnection, Uri.Host(host), port))
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

  def acceptedCharsetRanges: List[HttpCharsetRange] = {
    // TODO: sort by preference
    for (`Accept-Charset`(charsetRanges) ← headers; range ← charsetRanges) yield range
  }

  def acceptedEncodingRanges: List[HttpEncodingRange] = {
    // TODO: sort by preference
    for (`Accept-Encoding`(encodingRanges) ← headers; range ← encodingRanges) yield range
  }

  def cookies: List[HttpCookie] = for (`Cookie`(cookies) ← headers; cookie ← cookies) yield cookie

  /**
   * Determines whether the given media-type is accepted by the client.
   */
  def isMediaTypeAccepted(mediaType: MediaType) = {
    // according to the HTTP spec a client has to accept all mime types if no Accept header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1
    val ranges = acceptedMediaRanges
    ranges.isEmpty || ranges.exists(r ⇒ isMediaTypeMatched(r, mediaType))
  }

  private def isMediaTypeMatched(mediaRange: MediaRange, mediaType: MediaType) =
    // according to the HTTP spec a media range with a q-Value of 0 is not acceptable for the client
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.9
    mediaRange.qValue > 0.0f && mediaRange.matches(mediaType)

  /**
   * Determines whether the given charset is accepted by the client.
   */
  def isCharsetAccepted(charset: HttpCharset): Boolean = isCharsetAccepted(charset, acceptedCharsetRanges)

  private def isCharsetAccepted(charset: HttpCharset, ranges: List[HttpCharsetRange]): Boolean =
    // according to the HTTP spec a client has to accept all charsets if no Accept-Charset header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.2
    ranges.isEmpty || ranges.exists(_.matches(charset))

  /**
   * Determines whether the given encoding is accepted by the client.
   */
  def isEncodingAccepted(encoding: HttpEncoding) = {
    // according to the HTTP spec the server MAY assume that the client will accept any content coding if no
    // Accept-Encoding header is sent with the request (http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3)
    // this is what we do here
    val ranges = acceptedEncodingRanges
    ranges.isEmpty || ranges.exists(_.matches(encoding))
  }

  /**
   * Determines whether one of the given content-types is accepted by the client.
   * If a given ContentType does not define a charset an accepted charset is selected, i.e. the method guarantees
   * that, if a ContentType instance is returned within the option, it will contain a defined charset.
   */
  def acceptableContentType(contentTypes: Seq[ContentType]): Option[ContentType] = {
    val charsetRanges = acceptedCharsetRanges
    def hasAcceptedCharset(ct: ContentType) =
      ct.noCharsetDefined || isCharsetAccepted(ct.definedCharset.get, charsetRanges)
    val contentType = acceptedMediaRanges match {
      // according to the HTTP spec a client has to accept all mime types if no Accept header is sent with the request
      // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1
      case Nil ⇒ contentTypes find hasAcceptedCharset
      case mediaRanges ⇒ mediaRanges.view.flatMap { mediaRange ⇒
        contentTypes find { ct ⇒
          isMediaTypeMatched(mediaRange, ct.mediaType) && hasAcceptedCharset(ct)
        }
      }.headOption
    }
    contentType map { ct ⇒
      if (ct.isCharsetDefined) ct
      else {
        val acceptedCharset =
          if (isCharsetAccepted(`UTF-8`, charsetRanges)) `UTF-8`
          else charsetRanges match {
            case (cs: HttpCharset) :: _ ⇒ cs
            case _                      ⇒ throw new IllegalStateException // a HttpCharsetRange that is not `*` ?
          }
        ContentType(ct.mediaType, acceptedCharset)
      }
    }
  }

  def canBeRetried = method.isIdempotent

  def withHeaders(headers: List[HttpHeader]) = if (headers eq this.headers) this else copy(headers = headers)
  def withEntity(entity: HttpEntity) = if (entity eq this.entity) this else copy(entity = entity)
  def withHeadersAndEntity(headers: List[HttpHeader], entity: HttpEntity) =
    if ((headers eq this.headers) && (entity eq this.entity)) this else copy(headers = headers, entity = entity)
}

/**
 * Immutable HTTP response model.
 */
case class HttpResponse(status: StatusCode = StatusCodes.OK,
                        entity: HttpEntity = EmptyEntity,
                        headers: List[HttpHeader] = Nil,
                        protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`) extends HttpMessage with HttpResponsePart {
  type Self = HttpResponse

  def message = this
  def isRequest = false
  def isResponse = true

  def withHeaders(headers: List[HttpHeader]) = copy(headers = headers)
  def withEntity(entity: HttpEntity) = copy(entity = entity)
  def withHeadersAndEntity(headers: List[HttpHeader], entity: HttpEntity) = copy(headers = headers, entity = entity)

  def connectionCloseExpected: Boolean = protocol match {
    case HttpProtocols.`HTTP/1.0` ⇒ headers.forall { case x: Connection if x.hasKeepAlive ⇒ false; case _ ⇒ true }
    case HttpProtocols.`HTTP/1.1` ⇒ headers.exists { case x: Connection if x.hasClose ⇒ true; case _ ⇒ false }
  }
}

/**
 * Instance of this class represent the individual chunks of a chunked HTTP message (request or response).
 */
case class MessageChunk(body: Array[Byte], extension: String) extends HttpRequestPart with HttpResponsePart {
  require(body.length > 0, "MessageChunk must not have empty body")
  def bodyAsString: String = bodyAsString(HttpCharsets.`ISO-8859-1`.nioCharset)
  def bodyAsString(charset: HttpCharset): String = bodyAsString(charset.nioCharset)
  def bodyAsString(charset: Charset): String = if (body.isEmpty) "" else new String(body, charset)
  def bodyAsString(charset: String): String = if (body.isEmpty) "" else new String(body, charset)
  override def hashCode = extension.## * 31 + util.Arrays.hashCode(body)
  override def equals(obj: Any) = obj match {
    case x: MessageChunk ⇒ (this eq x) || extension == x.extension && util.Arrays.equals(body, x.body)
    case _               ⇒ false
  }
}

object MessageChunk {
  import HttpCharsets._
  def apply(body: String): MessageChunk =
    apply(body, "")
  def apply(body: String, charset: HttpCharset): MessageChunk =
    apply(body, charset, "")
  def apply(body: String, extension: String): MessageChunk =
    apply(body, `ISO-8859-1`, extension)
  def apply(body: String, charset: HttpCharset, extension: String): MessageChunk =
    apply(body.getBytes(charset.nioCharset), extension)
  def apply(body: Array[Byte]): MessageChunk =
    apply(body, "")
}

case class ChunkedRequestStart(request: HttpRequest) extends HttpMessageStart with HttpRequestPart {
  def message = request
}

case class ChunkedResponseStart(response: HttpResponse) extends HttpMessageStart with HttpResponsePart {
  def message = response
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