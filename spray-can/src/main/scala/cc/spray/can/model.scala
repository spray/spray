/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.can

import java.io.UnsupportedEncodingException
import java.net.InetAddress
import java.nio.charset.Charset

/**
 * Sealed trait modelling an HTTP method.
 * All defined methods are declared as members of the `HttpMethods` object.
 */
sealed trait HttpMethod {
  def name: String
}

/**
 * Module containing all defined [[cc.spray.can.HttpMethod]] instances.
 */
object HttpMethods {
  class Method private[HttpMethods] (val name: String) extends HttpMethod {
    override def toString = name
  }
  val GET     = new Method("GET")
  val POST    = new Method("POST")
  val PUT     = new Method("PUT")
  val DELETE  = new Method("DELETE")
  val HEAD    = new Method("HEAD")
  val OPTIONS = new Method("OPTIONS")
  val TRACE   = new Method("TRACE")
  val CONNECT = new Method("CONNECT")
}

/**
 * Sealed trait modelling an HTTP protocol version.
 * All defined protocols are declared as memebers of the `HttpProtocols` object.
 */
sealed trait HttpProtocol {
  def name: String
}

/**
 * Module containing all defined [[cc.spray.can.HttpProtocol]] instances.
 */
object HttpProtocols {
  class Protocol private[HttpProtocols] (val name: String) extends HttpProtocol {
    override def toString = name
  }
  val `HTTP/1.0` = new Protocol("HTTP/1.0")
  val `HTTP/1.1` = new Protocol("HTTP/1.1")
}

private[can] sealed trait MessageLine
private[can] case class RequestLine(
  method: HttpMethod = HttpMethods.GET,
  uri: String = "/",
  protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`
) extends MessageLine
private[can] case class StatusLine(requestMethod: HttpMethod, protocol: HttpProtocol, status: Int, reason: String) extends MessageLine

/**
 * The ''spray-can'' model of an HTTP header.
 */
case class HttpHeader(name: String, value: String) extends Product2[String, String] {
  def _1 = name
  def _2 = value
}

/**
 * The ''spray-can'' model of an HTTP request.
 */
case class HttpRequest(
  method: HttpMethod = HttpMethods.GET,
  uri: String = "/",
  headers: List[HttpHeader] = Nil,
  body: Array[Byte] = EmptyByteArray,
  protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`
) {
  def withBody(body: String, charset: String = "ISO-8859-1") = copy(body = body.getBytes(charset))
}

object HttpRequest {
  def verify(request: HttpRequest) = {
    import request._
    def req(cond: Boolean, msg: => String) { require(cond, "Illegal HttpRequest: " + msg) }
    req(method != null, "method must not be null")
    req(uri != null && !uri.isEmpty, "uri must not be null or empty")
    req(headers != null, "headers must not be null")
    req(body != null, "body must not be null (you can use cc.spray.can.EmptyByteArray for an empty body)")
    headers.foreach { header =>
      if (header.name == "Content-Length" || header.name == "Transfer-Encoding" || header.name == "Host")
        throw new IllegalArgumentException(header.name + " header must not be set explicitly, it is set automatically")
    }
    request
  }
}

/**
 * The ''spray-can'' model of an HTTP response.
 */
case class HttpResponse(
  status: Int = 200,
  headers: List[HttpHeader] = Nil,
  body: Array[Byte] = EmptyByteArray,
  protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`
) {
  def withBody(body: String, charset: String = "ISO-8859-1") = copy(body = body.getBytes(charset))

  def bodyAsString: String = if (body.isEmpty) "" else {
    val charset = headers.mapFind {
      case HttpHeader("Content-Type", HttpResponse.ContentTypeCharsetPattern(value)) => Some(value)
      case _ => None
    }
    try {
      new String(body, charset.getOrElse("ISO-8859-1"))
    } catch {
      case e: UnsupportedEncodingException => "<unsupported charset in Content-Type-Header>"
    }
  }
}

object HttpResponse {
  private val ContentTypeCharsetPattern = """.*charset=([-\w]+)""".r

  def verify(response: HttpResponse) = {
    import response._
    def req(cond: Boolean, msg: => String) { require(cond, "Illegal HttpResponse: " + msg) }
    req(100 <= status && status < 600, "Illegal HTTP status code: " + status)
    req(headers != null, "headers must not be null")
    req(body != null, "body must not be null (you can use cc.spray.can.EmptyByteArray for an empty body)")
    headers.foreach { header =>
      if (header.name == "Content-Length" || header.name == "Transfer-Encoding" || header.name == "Date")
        throw new IllegalArgumentException(header.name + " header must not be set explicitly, it is set automatically")
    }
    req(body.length == 0 || status / 100 > 1 && status != 204 && status != 304, "Illegal HTTP response: " +
            "responses with status code " + status + " must not have a message body")
    response
  }

  def defaultReason(statusCode: Int) = statusCode match {
    case 100 => "Continue"
    case 101 => "Switching Protocols"

    case 200 => "OK"
    case 201 => "Created"
    case 202 => "Accepted"
    case 203 => "Non-Authoritative Information"
    case 204 => "No Content"
    case 205 => "Reset Content"
    case 206 => "Partial Content"

    case 300 => "Multiple Choices"
    case 301 => "Moved Permanently"
    case 302 => "Found"
    case 303 => "See Other"
    case 304 => "Not Modified"
    case 305 => "Use Proxy"
    case 307 => "Temporary Redirect"

    case 400 => "Bad Request"
    case 401 => "Unauthorized"
    case 402 => "Payment Required"
    case 403 => "Forbidden"
    case 404 => "Not Found"
    case 405 => "Method Not Allowed"
    case 406 => "Not Acceptable"
    case 407 => "Proxy Authentication Required"
    case 408 => "Request Time-out"
    case 409 => "Conflict"
    case 410 => "Gone"
    case 411 => "Length Required"
    case 412 => "Precondition Failed"
    case 413 => "Request Entity Too Large"
    case 414 => "Request-URI Too Large"
    case 415 => "Unsupported Media Type"
    case 416 => "Requested range not satisfiable"
    case 417 => "Expectation Failed"

    case 500 => "Internal Server Error"
    case 501 => "Not Implemented"
    case 502 => "Bad Gateway"
    case 503 => "Service Unavailable"
    case 504 => "Gateway Time-out"
    case 505 => "HTTP Version not supported"
    case _   => "???"
  }
}

/**
 * An instance of this class serves as argument to the `streamActorCreator` function of the
 * [[cc.spray.can.ServerConfig]].
 */
case class ChunkedRequestContext(request: HttpRequest, remoteAddress: InetAddress)

/**
 * Receiver actors (see the `sendAndReceive` method of the [[cc.spray.can.HttpConnection]]) need to be able to handle
 * `ChunkedResponseStart` messages, which signal the arrival of a chunked (streaming) response.
 */
case class ChunkedResponseStart(status: Int, headers: List[HttpHeader])

/**
 * Stream actors (see the `streamActorCreator` member of the [[cc.spray.can.ServerConfig]]) need to be able to handle
 * `ChunkedRequestEnd` messages, which represent the end of an incoming chunked (streaming) request.
 */
case class ChunkedRequestEnd(
  extensions: List[ChunkExtension],
  trailer: List[HttpHeader],
  responder: RequestResponder
)

/**
 * Receiver actors (see the `sendAndReceive` method of the [[cc.spray.can.HttpConnection]]) need to be able to handle
 * `ChunkedResponseEnd` messages, which represent the end of an incoming chunked (streaming) response.
 */
case class ChunkedResponseEnd(
  extensions: List[ChunkExtension],
  trailer: List[HttpHeader]
)

/**
 * Instance of this class represent the individual chunks of a chunked HTTP message (request or response).
 */
case class MessageChunk(body: Array[Byte], extensions: List[ChunkExtension]) {
  require(body.length > 0, "MessageChunk must not have empty body")
  def bodyAsString: String = bodyAsString("ISO-88591-1")
  def bodyAsString(charset: Charset): String = if (body.isEmpty) "" else new String(body, charset)
  def bodyAsString(charset: String): String = if (body.isEmpty) "" else new String(body, charset)
}

object MessageChunk {
  def apply(body: String): MessageChunk =
    apply(body, Nil)
  def apply(body: String, charset: String): MessageChunk =
    apply(body, charset, Nil)
  def apply(body: String, extensions: List[ChunkExtension]): MessageChunk =
    apply(body, "ISO-8859-1", extensions)
  def apply(body: String, charset: String, extensions: List[ChunkExtension]): MessageChunk =
    apply(body.getBytes(charset), extensions)
  def apply(body: Array[Byte]): MessageChunk =
    apply(body, Nil)
}

case class ChunkExtension(name: String, value: String)

object Trailer {
  def verify(trailer: List[HttpHeader]) = {
    if (!trailer.isEmpty) {
      require(trailer.forall(_.name != "Content-Length"), "Content-Length header is not allowed in trailer")
      require(trailer.forall(_.name != "Transfer-Encoding"), "Transfer-Encoding header is not allowed in trailer")
      require(trailer.forall(_.name != "Trailer"), "Trailer header is not allowed in trailer")
    }
    trailer
  }
}