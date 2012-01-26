/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.can.model

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