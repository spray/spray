/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.http

import java.net.URI
import HttpHeaders._
import HttpVersions._
import parser.QueryParser

/**
 * Sprays immutable model of an HTTP request.
 */
case class HttpRequest(method: HttpMethod = HttpMethods.GET,
                       uri: String = "",
                       headers: List[HttpHeader] = Nil,
                       content: Option[HttpContent] = None,
                       remoteHost: Option[HttpIp] = None,
                       version: Option[HttpVersion] = Some(`HTTP/1.1`)) {
  
  lazy val URI = new URI(uri)
  
  def path = nonNull(URI.getPath)
  def host = nonNull(URI.getHost)
  def port = URI.getPort
  def query = nonNull(URI.getQuery)
  def fragment = nonNull(URI.getFragment)
  def authority = nonNull(URI.getAuthority)
  def scheme = nonNull(URI.getScheme)
  def schemeSpecificPart = nonNull(URI.getSchemeSpecificPart)
  def userInfo = nonNull(URI.getUserInfo)
  def isUriAbsolute = URI.isAbsolute
  def isUriOpaque = URI.isOpaque

  private def nonNull(s: String, default: String = ""): String = if (s == null) default else s
  
  def withUri(scheme: String = this.scheme,
              userInfo: String = this.userInfo,
              host: String = this.host,
              port: Int = this.port,
              path: String = this.path,
              query: String = this.query,
              fragment: String = this.fragment) = {
    copy(uri = new URI(scheme, userInfo, host, port, path, query, fragment).toString)
  }
  
  lazy val acceptedMediaRanges: List[MediaRange] = {
    // TODO: sort by preference
    for (Accept(mediaRanges) <- headers; range <- mediaRanges) yield range
  }
  
  lazy val acceptedCharsetRanges: List[CharsetRange] = {
    // TODO: sort by preference
    for (`Accept-Charset`(charsetRanges) <- headers; range <- charsetRanges) yield range
  }

  def isContentTypeAccepted(contentType: ContentType) = {
    isMediaTypeAccepted(contentType.mediaType) && isCharsetAccepted(contentType.charset)  
  } 
  
  def isMediaTypeAccepted(mediaType: MediaType) = {
    // according to the HTTP spec a client has to accept all mime types if no Accept header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1
    acceptedMediaRanges.isEmpty || acceptedMediaRanges.exists(_.matches(mediaType))
  }
  
  def isCharsetAccepted(charset: Option[Charset]) = {
    // according to the HTTP spec a client has to accept all charsets if no Accept-Charset header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.2
    acceptedCharsetRanges.isEmpty || charset.isDefined && acceptedCharsetRanges.exists(_.matches(charset.get))
  }
  
  lazy val queryParams: Map[String, String] = QueryParser.parse(query)
}
