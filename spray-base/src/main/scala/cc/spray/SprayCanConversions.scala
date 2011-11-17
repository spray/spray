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
import MediaTypes._
import HttpMethods._
import HttpHeaders._
import HttpProtocols._
import collection.mutable.ListBuffer
import collection.breakOut

trait SprayCanConversions {

  def fromSprayCanRequest(request: can.HttpRequest) = {
    val (contentType, _, regularHeaders) = parseFromRaw(request.headers)
    HttpRequest(
      method = fromSprayCanMethod(request.method),
      uri = request.uri,
      headers = regularHeaders,
      content = fromSprayCanBody(contentType, request.body),
      protocol = fromSprayCanProtocol(request.protocol)
    )
  }

  def toSprayCanRequest(request: HttpRequest) = {
    can.HttpRequest(
      method = toSprayCanMethod(request.method),
      uri = request.uri,
      headers = toSprayCanHeaders(request.headers, request.content),
      body = toSprayCanBody(request.content)
    )
  }

  protected def fromSprayCanMethod(method: can.HttpMethod) = method match {
    case can.HttpMethods.GET     => GET
    case can.HttpMethods.POST    => POST
    case can.HttpMethods.PUT     => PUT
    case can.HttpMethods.DELETE  => DELETE
    case can.HttpMethods.HEAD    => HEAD
    case can.HttpMethods.OPTIONS => OPTIONS
    case can.HttpMethods.TRACE   => TRACE
    case can.HttpMethods.CONNECT => throw new HttpException(StatusCodes.MethodNotAllowed)
  }

  protected def toSprayCanMethod(method: HttpMethod) = method match {
    case GET     => can.HttpMethods.GET
    case POST    => can.HttpMethods.POST
    case PUT     => can.HttpMethods.PUT
    case DELETE  => can.HttpMethods.DELETE
    case HEAD    => can.HttpMethods.HEAD
    case OPTIONS => can.HttpMethods.OPTIONS
    case TRACE   => can.HttpMethods.TRACE
    case CONNECT => can.HttpMethods.CONNECT
  }

  protected def fromSprayCanProtocol(protocol: can.HttpProtocol) = protocol match {
    case can.HttpProtocols.`HTTP/1.0` => `HTTP/1.0`
    case can.HttpProtocols.`HTTP/1.1` => `HTTP/1.1`
  }

  protected def toSprayCanProtocol(protocol: HttpProtocol) = protocol match {
    case `HTTP/1.0` => can.HttpProtocols.`HTTP/1.0`
    case `HTTP/1.1` => can.HttpProtocols.`HTTP/1.1`
  }

  protected def fromSprayCanBody(contentTypeHeader: Option[HttpHeaders.`Content-Type`], body: Array[Byte]) = {
    if (body.length > 0) {
      val contentType = contentTypeHeader.map(_.contentType).getOrElse(ContentType(`application/octet-stream`))
      Some(HttpContent(contentType, body))
    } else None
  }

  protected def toSprayCanBody(content: Option[HttpContent]) = {
    content.map(_.buffer).getOrElse(can.EmptyByteArray)
  }

  protected def fromSprayCanResponse(response: can.HttpResponse) = {
    val (contentType, _, regularHeaders) = parseFromRaw(response.headers)
    new HttpResponse(
      status = response.status,
      headers = regularHeaders,
      content = fromSprayCanBody(contentType, response.body),
      protocol = fromSprayCanProtocol(response.protocol)
    )
  }

  protected def toSprayCanResponse(response: HttpResponse) = can.HttpResponse(
    status = response.status.value,
    headers = toSprayCanHeaders(response.headers, response.content),
    body = toSprayCanBody(response.content),
    protocol = toSprayCanProtocol(response.protocol)
  )

  protected def toSprayCanHeaders(headers: List[HttpHeader], content: Option[HttpContent]) = {
    val canHeaders: ListBuffer[can.HttpHeader] = headers.map(h => can.HttpHeader(h.name, h.value)) (breakOut)
    content.foreach(c => canHeaders += can.HttpHeader("Content-Type", c.contentType.value))
    canHeaders.toList
  }
}