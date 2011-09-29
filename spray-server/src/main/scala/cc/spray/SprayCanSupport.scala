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

package cc.spray

import http._
import HttpHeaders._
import MediaTypes._
import java.net.InetAddress

trait SprayCanSupport {

  protected def fromSprayCanContext(request: can.HttpRequest, remoteAddress: InetAddress,
                                    responder: can.HttpResponse => Unit) = {
    RequestContext(
      request = fromSprayCanRequest(request),
      remoteHost = HttpIp(remoteAddress),
      responder = fromSprayCanResponder(responder)
    )
  }

  protected def fromSprayCanRequest(req: can.HttpRequest) = {
    val (contentType, _, regularHeaders) = HttpHeaders.parseFromRaw(req.headers)
    HttpRequest(
      method = fromSprayCanMethod(req.method),
      uri = req.uri,
      headers = regularHeaders,
      content = fromSprayCanBody(contentType, req.body),
      protocol = fromSprayCanProtocol(req.protocol)
    )
  }

  protected def fromSprayCanMethod(method: can.HttpMethod) = method match {
    case can.HttpMethods.GET     => HttpMethods.GET
    case can.HttpMethods.POST    => HttpMethods.POST
    case can.HttpMethods.PUT     => HttpMethods.PUT
    case can.HttpMethods.DELETE  => HttpMethods.DELETE
    case can.HttpMethods.HEAD    => HttpMethods.HEAD
    case can.HttpMethods.OPTIONS => HttpMethods.OPTIONS
    case can.HttpMethods.TRACE   => HttpMethods.TRACE
    case can.HttpMethods.CONNECT => HttpMethods.CONNECT
  }

  protected def fromSprayCanProtocol(protocol: can.HttpProtocol) = protocol match {
    case can.HttpProtocols.`HTTP/1.0` => HttpProtocols.`HTTP/1.0`
    case can.HttpProtocols.`HTTP/1.1` => HttpProtocols.`HTTP/1.1`
  }

  protected def fromSprayProtocol(protocol: HttpProtocol) = protocol match {
    case HttpProtocols.`HTTP/1.0` => can.HttpProtocols.`HTTP/1.0`
    case HttpProtocols.`HTTP/1.1` => can.HttpProtocols.`HTTP/1.1`
  }

  protected def fromSprayCanBody(contentTypeHeader: Option[`Content-Type`], body: Array[Byte]) = {
    if (body.length > 0) {
      val contentType = contentTypeHeader.map(_.contentType).getOrElse(ContentType(`application/octet-stream`))
      Some(HttpContent(contentType, body))
    } else None
  }

  protected def fromSprayCanResponder(responder: can.HttpResponse => Unit)(routingResult: RoutingResult) {
    routingResult match {
      case Respond(response) => responder(fromSprayResponse(response))
      case _: Reject => throw new IllegalStateException
    }
  }

  protected def fromSprayResponse(response: HttpResponse) = {
    can.HttpResponse(
      status = response.status.value,
      headers = response.headers.map(h => can.HttpHeader(h.name, h.value)),
      body = response.content.map(_.buffer).getOrElse(can.EmptyByteArray),
      protocol = fromSprayProtocol(response.protocol)
    )
  }
}
