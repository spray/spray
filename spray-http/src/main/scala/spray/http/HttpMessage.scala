/*
 * Copyright (C) 2011-2012 spray.io
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

import java.nio.charset.Charset
import java.net.{URISyntaxException, URI}
import scala.annotation.tailrec
import scala.reflect.{classTag, ClassTag}
import spray.http.parser.{QueryParser, HttpParser}
import HttpHeaders._
import HttpCharsets._
import StatusCodes._


sealed trait HttpMessagePartWrapper {
  def messagePart: HttpMessagePart
  def ack: Any
}

case class Confirmed(messagePart: HttpMessagePart, ack: Any) extends HttpMessagePartWrapper

object HttpMessagePartWrapper {
  def unapply(x: HttpMessagePartWrapper): Option[(HttpMessagePart, Any)] = Some((x.messagePart, x.ack))
}

sealed trait HttpMessagePart extends HttpMessagePartWrapper {
  def messagePart = this
  def ack: Any = None // we use `None` as the special value signalling "No Ack requested"
  def withAck(ack: Any) = Confirmed(this, ack)
}

sealed trait HttpRequestPart extends HttpMessagePart

object HttpRequestPart {
  def unapply(wrapper: HttpMessagePartWrapper): Option[(HttpRequestPart, Any)] =
    wrapper.messagePart match {
      case x: HttpRequestPart => Some((x, wrapper.ack))
      case _ => None
    }
}

sealed trait HttpResponsePart extends HttpMessagePart

object HttpResponsePart {
  def unapply(wrapper: HttpMessagePartWrapper): Option[(HttpResponsePart, Any)] =
    wrapper.messagePart match {
      case x: HttpResponsePart => Some((x, wrapper.ack))
      case _ => None
    }
}

sealed trait HttpMessageStart extends HttpMessagePart {
  def message: HttpMessage
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

  /**
   * Tries to parse all RawHeaders in the headers list that spray has a higher-level model for and
   * returns an error message together with a copy of this message with the headers list updated respectively.
   * If there were no errors in the header parsing process the returned error string is empty.
   * Otherwise the error message is accompanied by a new message object in which all headers that were parsed
   * without errors have been "upgraded". Invalid headers remain RawHeader instances in this case.
   */
  def parseHeaders: (String, Self) = {
    val (errors, parsed) = HttpParser.parseHeaders(headers)
    val errorMsg = if (errors.isEmpty) "" else "HTTP message contains illegal headers: " + errors.mkString(", ")
    (errorMsg, withHeaders(parsed))
  }

  def parseHeadersToEither: Either[String, Self] = parseHeaders match {
    case ("", self) => Right(self)
    case (errorMsg, _) => Left(errorMsg)
  }

  def withHeaders(headers: List[HttpHeader]): Self
  def withEntity(entity: HttpEntity): Self
  def withHeadersAndEntity(headers: List[HttpHeader], entity: HttpEntity): Self

  def mapHeaders(f: List[HttpHeader] => List[HttpHeader]): Self = withHeaders(f(headers))
  def mapEntity(f: HttpEntity => HttpEntity): Self = withEntity(f(entity))

  /**
   * Returns true if a Content-Encoding header is present. 
   */
  def isEncodingSpecified: Boolean = headers.exists(_.isInstanceOf[`Content-Encoding`])

  /**
   * The content encoding as specified by the Content-Encoding header. If no Content-Encoding header is present the
   * default value 'identity' is returned.
   */
  def encoding = headers.collect { case `Content-Encoding`(enc) => enc } match {
    case enc :: _ => enc
    case Nil => HttpEncodings.identity
  }

  def header[T <: HttpHeader :ClassTag]: Option[T] = {
    val erasure = classTag[T].runtimeClass
    @tailrec def next(headers: List[HttpHeader]): Option[T] =
      if (headers.isEmpty) None
      else if (erasure.isInstance(headers.head)) Some(headers.head.asInstanceOf[T]) else next(headers.tail)
    next(headers)
  }

  def as[T](implicit f: Self => T): T = f(message)
}


/**
 * Sprays immutable model of an HTTP request.
 * The `uri` member contains the the undecoded URI of the request as it appears in the HTTP message,
 * i.e. just the path, query and fragment string without scheme and authority (host and port).
 */
final class HttpRequest private(
  val method: HttpMethod,
  val uri: String,
  val headers: List[HttpHeader],
  val entity: HttpEntity,
  val protocol: HttpProtocol,
  val queryParams: QueryParams, // empty for instances not created by `parseQuery`
  URI: URI // non-public, only used internally for caching `parseUri` result, TODO: replace with custom URI parsing
  ) extends HttpMessage with HttpRequestPart {

  type Self = HttpRequest

  def message = this
  def isRequest = true
  def isResponse = false

  def scheme      = if (URI.getScheme      == null) "" else URI.getScheme
  def uriHost     = if (URI.getHost        == null) "" else URI.getHost
  def uriPort     = if (URI.getPort        == -1) None else Some(URI.getPort)
  def path        = if (URI.getPath        == null) "" else URI.getPath
  def rawPath     = if (URI.getRawPath     == null) "" else URI.getRawPath
  def query       = if (URI.getQuery       == null) "" else URI.getQuery
  def rawQuery    = if (URI.getRawQuery    == null) "" else URI.getRawQuery
  def fragment    = if (URI.getFragment    == null) "" else URI.getFragment
  def rawFragment = if (URI.getRawFragment == null) "" else URI.getRawFragment

  def rawPathQueryFragment: String = {
    val sb = new java.lang.StringBuilder(rawPath)
    val rq = rawQuery
    val rf = rawFragment
    if (!rq.isEmpty) sb.append('?').append(rq)
    if (!rf.isEmpty) sb.append('#').append(rf)
    if (sb.length == 0) "/" else sb.toString
  }

  def host: String = hostHeader.map(_.host).getOrElse("")
  def port: Option[Int] = hostHeader.map(_.port).getOrElse(None)
  def hostAndPort: String = hostHeader.map(_.value).getOrElse("")
  def hostHeader: Option[Host] = hostHeader(headers)

  @tailrec
  private def hostHeader(headers: List[HttpHeader]): Option[Host] = headers match {
    case Nil => None
    case (x: Host) :: _ => Some(x)
    case _ => hostHeader(headers.tail)
  }

  /**
   * Parses the `uri` to create a copy of this request with the `URI` member updated
   * or throws an HttpException if the `uri` cannot be parsed.
   *
   * @throws HttpException with status = BadRequest if the uri is illegal
   */
  def parseUri: HttpRequest = {
    try {
      if (URI eq HttpRequest.DefaultURI) internalCopy(URI = new URI(uri)) else this
    } catch {
      case e: URISyntaxException => throw new IllegalRequestException(BadRequest, "Illegal URI", e.getMessage)
    }
  }

  /**
   * Parses the query string to create a copy of this request with the `queryParams` member updated.
   * If the query string cannot be parsed an HttpException will be thrown.
   * This method will implicitly call `parseUri` if this has not yet been done for this request.
   *
   * @throws HttpException with status = BadRequest if the query string is illegal
   */
  def parseQuery: HttpRequest = {
    def doParseQuery(req: HttpRequest) = {
      if (!req.rawQuery.isEmpty) {
        QueryParser.parseQueryString(req.rawQuery) match {
          case Right(params) => req.internalCopy(queryParams = params)
          case Left(errorInfo) => throw new IllegalRequestException(BadRequest, errorInfo)
        }
      } else req
    }
    if (URI eq HttpRequest.DefaultURI) doParseQuery(parseUri)
    else doParseQuery(this)
  }

  /**
   * Parses the headers, uri and query string of this request and returns either a new HttpRequest with all
   * the parsed data in place or throws an HttpException
   *
   * @throws HttpException with status = BadRequest if the query string, uri or a header is illegal
   */
  def parseAll: HttpRequest = parseHeadersToEither match {
    case Right(request) => request.parseQuery
    case Left(errorMsg) => throw new IllegalRequestException(BadRequest, RequestErrorInfo(errorMsg))
  }

  def copy(method: HttpMethod = method,
           uri: String = uri,
           headers: List[HttpHeader] = headers,
           entity: HttpEntity = entity,
           protocol: HttpProtocol = protocol): HttpRequest =
    if (uri != this.uri) HttpRequest(method, uri, headers, entity, protocol)
    else new HttpRequest(method, uri, headers, entity, protocol, queryParams, URI)

  private def internalCopy(method: HttpMethod = method,
                           uri: String = uri,
                           headers: List[HttpHeader] = headers,
                           entity: HttpEntity = entity,
                           protocol: HttpProtocol = protocol,
                           queryParams: QueryParams = queryParams,
                           URI: URI = URI): HttpRequest =
    new HttpRequest(method, uri, headers, entity, protocol, queryParams, URI)

  override def hashCode(): Int = (((((method.## * 31) + uri.##) * 31) + headers.##) * 31 + entity.##) + protocol.##
  override def equals(that: Any) = that match {
    case x: HttpRequest => (this eq x) ||
      method == x.method && uri == x.uri && headers == x.headers && entity == x.entity && protocol == x.protocol
    case _ => false
  }
  override def toString = "HttpRequest(%s, %s, %s, %s, %s)" format (method, uri, headers, entity, protocol)

  def acceptedMediaRanges: List[MediaRange] = {
    // TODO: sort by preference
    for (Accept(mediaRanges) <- headers; range <- mediaRanges) yield range
  }

  def acceptedCharsetRanges: List[HttpCharsetRange] = {
    // TODO: sort by preference
    for (`Accept-Charset`(charsetRanges) <- headers; range <- charsetRanges) yield range
  }

  def acceptedEncodingRanges: List[HttpEncodingRange] = {
    // TODO: sort by preference
    for (`Accept-Encoding`(encodingRanges) <- headers; range <- encodingRanges) yield range
  }

  def cookies: List[HttpCookie] = for (`Cookie`(cookies) <- headers; cookie <- cookies) yield cookie

  /**
   * Determines whether the given mediatype is accepted by the client.
   */
  def isMediaTypeAccepted(mediaType: MediaType) = {
    // according to the HTTP spec a client has to accept all mime types if no Accept header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1
    val ranges = acceptedMediaRanges
    ranges.isEmpty || ranges.exists(_.matches(mediaType))
  }

  /**
   * Determines whether the given charset is accepted by the client.
   */
  def isCharsetAccepted(charset: HttpCharset) = {
    // according to the HTTP spec a client has to accept all charsets if no Accept-Charset header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.2
    val ranges = acceptedCharsetRanges
    ranges.isEmpty || ranges.exists(_.matches(charset))
  }

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
   * Determines whether the given content-type is accepted by the client.
   */
  def isContentTypeAccepted(ct: ContentType) = {
    isMediaTypeAccepted(ct.mediaType) && (ct.noCharsetDefined || isCharsetAccepted(ct.definedCharset.get))
  }

  /**
   * Determines whether the given content-type is accepted by the client.
   * If the given ContentType does not define a charset an accepted charset is selected, i.e. the method guarantees
   * that, if a ContentType instance is returned within the option, it will contain a defined charset.
   */
  def acceptableContentType(contentType: ContentType): Option[ContentType] = {
    if (isContentTypeAccepted(contentType)) Some {
      if (contentType.isCharsetDefined) contentType
      else ContentType(contentType.mediaType, acceptedCharset)
    } else None
  }

  /**
   * Returns a charset that is accepted by the client.
   * Default is UTF-8 in that, if UTF-8 is accepted, it is used.
   */
  def acceptedCharset: HttpCharset = {
    if (isCharsetAccepted(`UTF-8`)) `UTF-8`
    else acceptedCharsetRanges match {
      case (cs: HttpCharset) :: _ => cs
      case _ => throw new IllegalStateException // a HttpCharsetRange that is not `*` ?
    }
  }

  def canBeRetried = method.isIdempotent

  def withHeaders(headers: List[HttpHeader]) = if (headers eq this.headers) this else internalCopy(headers = headers)
  def withEntity(entity: HttpEntity) = if (entity eq this.entity) this else internalCopy(entity = entity)
  def withHeadersAndEntity(headers: List[HttpHeader], entity: HttpEntity) =
    if ((headers eq this.headers) && (entity eq this.entity)) this else internalCopy(headers = headers, entity = entity)
}

object HttpRequest {
  val DefaultURI = new URI("")

  def apply(method: HttpMethod = HttpMethods.GET,
            uri: String = "/",
            headers: List[HttpHeader] = Nil,
            entity: HttpEntity = EmptyEntity,
            protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`): HttpRequest = {
    new HttpRequest(method, uri, headers, entity, protocol, Map.empty, DefaultURI)
  }

  def unapply(request: HttpRequest): Option[(HttpMethod, String, List[HttpHeader], HttpEntity, HttpProtocol)] = {
    import request._
    Some((method, uri, headers, entity, protocol))
  }
}


/**
 * Sprays immutable model of an HTTP response.
 */
case class HttpResponse(status: StatusCode = StatusCodes.OK,
                        entity: HttpEntity = EmptyEntity,
                        headers: List[HttpHeader] = Nil,
                        protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`) extends HttpMessage with HttpResponsePart{
  type Self = HttpResponse

  def message = this
  def isRequest = false
  def isResponse = true

  def withHeaders(headers: List[HttpHeader]) = copy(headers = headers)
  def withEntity(entity: HttpEntity) = copy(entity = entity)
  def withHeadersAndEntity(headers: List[HttpHeader], entity: HttpEntity) = copy(headers = headers, entity = entity)
}

/**
 * Instance of this class represent the individual chunks of a chunked HTTP message (request or response).
 */
case class MessageChunk(body: Array[Byte], extensions: List[ChunkExtension]) extends HttpRequestPart with HttpResponsePart {
  require(body.length > 0, "MessageChunk must not have empty body")
  def bodyAsString: String = bodyAsString(HttpCharsets.`ISO-8859-1`.nioCharset)
  def bodyAsString(charset: HttpCharset): String = bodyAsString(charset.nioCharset)
  def bodyAsString(charset: Charset): String = if (body.isEmpty) "" else new String(body, charset)
  def bodyAsString(charset: String): String = if (body.isEmpty) "" else new String(body, charset)
}

object MessageChunk {
  import HttpCharsets._
  def apply(body: String): MessageChunk =
    apply(body, Nil)
  def apply(body: String, charset: HttpCharset): MessageChunk =
    apply(body, charset, Nil)
  def apply(body: String, extensions: List[ChunkExtension]): MessageChunk =
    apply(body, `ISO-8859-1`, extensions)
  def apply(body: String, charset: HttpCharset, extensions: List[ChunkExtension]): MessageChunk =
    apply(body.getBytes(charset.nioCharset), extensions)
  def apply(body: Array[Byte]): MessageChunk =
    apply(body, Nil)
}

case class ChunkedRequestStart(request: HttpRequest) extends HttpMessageStart with HttpRequestPart {
  def message = request
}

case class ChunkedResponseStart(response: HttpResponse) extends HttpMessageStart with HttpResponsePart {
  def message = response
}

case class ChunkedMessageEnd(
  extensions: List[ChunkExtension] = Nil,
  trailer: List[HttpHeader] = Nil
) extends HttpRequestPart with HttpResponsePart with HttpMessageEnd {
  if (!trailer.isEmpty) {
    require(trailer.forall(_.isNot("content-length")), "Content-Length header is not allowed in trailer")
    require(trailer.forall(_.isNot("transfer-encoding")), "Transfer-Encoding header is not allowed in trailer")
    require(trailer.forall(_.isNot("trailer")), "Trailer header is not allowed in trailer")
  }
}

case class ChunkExtension(name: String, value: String)