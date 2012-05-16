/*
 * Copyright (C) 2011-2012 spray.cc
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

object SprayCanConversions {

  def fromSprayCanRequest(request: can.model.HttpRequest) = {
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
    can.model.HttpRequest(
      method = toSprayCanMethod(request.method),
      uri = request.uri,
      headers = toSprayCanHeaders(request.headers, request.content),
      body = toSprayCanBody(request.content)
    )
  }

  def fromSprayCanMethod(method: can.model.HttpMethod) = method match {
    case can.model.HttpMethods.GET     => GET
    case can.model.HttpMethods.POST    => POST
    case can.model.HttpMethods.PUT     => PUT
    case can.model.HttpMethods.DELETE  => DELETE
    case can.model.HttpMethods.HEAD    => HEAD
    case can.model.HttpMethods.OPTIONS => OPTIONS
    case can.model.HttpMethods.TRACE   => TRACE
    case can.model.HttpMethods.CONNECT => throw HttpException(StatusCodes.MethodNotAllowed)
  }

  def toSprayCanMethod(method: HttpMethod) = method match {
    case GET     => can.model.HttpMethods.GET
    case POST    => can.model.HttpMethods.POST
    case PUT     => can.model.HttpMethods.PUT
    case DELETE  => can.model.HttpMethods.DELETE
    case HEAD    => can.model.HttpMethods.HEAD
    case OPTIONS => can.model.HttpMethods.OPTIONS
    case TRACE   => can.model.HttpMethods.TRACE
  }

  def fromSprayCanProtocol(protocol: can.model.HttpProtocol) = protocol match {
    case can.model.HttpProtocols.`HTTP/1.0` => `HTTP/1.0`
    case can.model.HttpProtocols.`HTTP/1.1` => `HTTP/1.1`
  }

  def toSprayCanProtocol(protocol: HttpProtocol) = protocol match {
    case `HTTP/1.0` => can.model.HttpProtocols.`HTTP/1.0`
    case `HTTP/1.1` => can.model.HttpProtocols.`HTTP/1.1`
  }

  def fromSprayCanBody(contentTypeHeader: Option[HttpHeaders.`Content-Type`], body: Array[Byte]) = {
    if (body.length > 0) {
      val contentType = contentTypeHeader.map(_.contentType).getOrElse(ContentType(`application/octet-stream`))
      Some(HttpContent(contentType, body))
    } else None
  }

  def toSprayCanBody(content: Option[HttpContent]) = {
    content.map(_.buffer).getOrElse(util.EmptyByteArray)
  }

  def fromSprayCanResponse(response: can.model.HttpResponse) = {
    val (contentType, _, regularHeaders) = parseFromRaw(response.headers)
    new HttpResponse(
      status = response.status,
      headers = regularHeaders,
      content = fromSprayCanBody(contentType, response.body),
      protocol = fromSprayCanProtocol(response.protocol)
    )
  }

  def toSprayCanResponse(response: HttpResponse) = can.model.HttpResponse(
    status = response.status.value,
    headers = toSprayCanHeaders(response.headers, response.content),
    body = toSprayCanBody(response.content),
    protocol = toSprayCanProtocol(response.protocol)
  )

  def toSprayCanHeaders(headers: List[HttpHeader], content: Option[HttpContent]) = {
    val canHeaders: ListBuffer[can.model.HttpHeader] = headers.map(toSprayCanHeader) (breakOut)
    content.foreach(c => canHeaders += can.model.HttpHeader("Content-Type", c.contentType.value))
    canHeaders.toList
  }

  def toSprayCanHeader(header: HttpHeader) = can.model.HttpHeader(header.name, header.value)

  def toSprayCanMessageChunk(chunk: MessageChunk) =
    can.model.MessageChunk(chunk.body, chunk.extensions.map(toSprayCanChunkExtension))

  def toSprayCanChunkExtension(ext: ChunkExtension) = can.model.ChunkExtension(ext.name, ext.value)
}