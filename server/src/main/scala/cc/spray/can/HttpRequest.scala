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

import java.net.InetAddress

sealed trait HttpMethod {
  def name: String
}
object HttpMethods {
  class Method private[HttpMethods] (val name: String) extends HttpMethod {
    override def toString = name
  }
  val GET = new Method("GET")
  val POST = new Method("POST")
  val PUT = new Method("PUT")
  val DELETE = new Method("DELETE")
  val HEAD = new Method("HEAD")
  val OPTIONS = new Method("OPTIONS")
  val TRACE = new Method("TRACE")
  val CONNECT = new Method("CONNECT")
}

sealed trait HttpProtocol {
  def name: String
}

object HttpProtocols {
  class Protocol private[HttpProtocols] (val name: String) extends HttpProtocol {
    override def toString = name
  }
  val `HTTP/1.0` = new Protocol("HTTP/1.0")
  val `HTTP/1.1` = new Protocol("HTTP/1.1")
}

sealed trait MessageLine
case class RequestLine(method: HttpMethod, uri: String, protocol: HttpProtocol) extends MessageLine
case class StatusLine(protocol: HttpProtocol, status: Int, reason: String) extends MessageLine

case class HttpHeader(name: String, value: String)

case class HttpRequest(
  method: HttpMethod,
  uri: String,
  protocol: HttpProtocol,
  headers: List[HttpHeader],
  body: Array[Byte],
  remoteIP: InetAddress
)

case class RequestContext(request: HttpRequest, responder: HttpResponse => Unit)

case class HttpResponse(
  status: Int = 200,
  headers: List[HttpHeader] = Nil,
  body: Array[Byte] = EmptyByteArray
) {
  require(100 <= status && status < 600, "Illegal HTTP status code: " + status)
  require(headers != null, "headers must not be null")
  require(body != null, "body must not be null (use cc.spray.can.EmptyByteArray for empty body)")
  require(headers.forall(_.name != "Content-Length"), "Content-Length header must not be present, the server sets it itself")
  require(body.length == 0 || status / 100 > 1 && status != 204 && status != 304, "Illegal HTTP response: " +
          "responses with status code " + status + " must not have a message body")

  def bodyAsString(charset: String = "ISO-8859-1") = new String(body, charset)
}

object HttpResponse {
  def of(status: Int = 200, headers: List[HttpHeader] = Nil, body: String = "") =
    HttpResponse(status, headers, if (body.isEmpty) EmptyByteArray else body.getBytes("ISO-8859-1"))

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