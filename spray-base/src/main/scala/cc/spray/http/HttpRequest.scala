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
import HttpCharsets._
import HttpProtocols._
import parser.QueryParser

/**
 * Sprays immutable model of an HTTP request.
 */
case class HttpRequest(method: HttpMethod = HttpMethods.GET,
                       uri: String = "/",
                       headers: List[HttpHeader] = Nil,
                       content: Option[HttpContent] = None,
                       protocol: HttpProtocol = `HTTP/1.1`) extends HttpMessage {
  
  lazy val URI = new URI(uri)
  
  lazy val queryParams: Map[String, String] = QueryParser.parse(query)
  
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
  
  lazy val acceptedCharsetRanges: List[HttpCharsetRange] = {
    // TODO: sort by preference
    for (`Accept-Charset`(charsetRanges) <- headers; range <- charsetRanges) yield range
  }
  
  lazy val acceptedEncodingRanges: List[HttpEncodingRange] = {
    // TODO: sort by preference
    for (`Accept-Encoding`(encodingRanges) <- headers; range <- encodingRanges) yield range
  }

  lazy val cookies: List[HttpCookie] = for (`Cookie`(cookies) <- headers; cookie <- cookies) yield cookie

  /**
   * Determines whether the given mediatype is accepted by the client.
   */
  def isMediaTypeAccepted(mediaType: MediaType) = {
    // according to the HTTP spec a client has to accept all mime types if no Accept header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1
    acceptedMediaRanges.isEmpty || acceptedMediaRanges.exists(_.matches(mediaType))
  }

  /**
   * Determines whether the given charset is accepted by the client.
   */
  def isCharsetAccepted(charset: HttpCharset) = {
    // according to the HTTP spec a client has to accept all charsets if no Accept-Charset header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.2
    acceptedCharsetRanges.isEmpty || acceptedCharsetRanges.exists(_.matches(charset))
  }

  /**
   * Determines whether the given encoding is accepted by the client.
   */
  def isEncodingAccepted(encoding: HttpEncoding) = {
    // according to the HTTP spec the server MAY assume that the client will accept any content coding if no
    // Accept-Encoding header is sent with the request (http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3)
    // this is what we do here
    acceptedEncodingRanges.isEmpty || acceptedEncodingRanges.exists(_.matches(encoding))
  }

  /**
   * Determines whether the given content-type is accepted by the client.
   */
  def isContentTypeAccepted(ct: ContentType) = {
    isMediaTypeAccepted(ct.mediaType) && (ct.charset.isEmpty || isCharsetAccepted(ct.charset.get))
  }

  /**
   * Determines whether the given content-type is accepted by the client.
   * If the given content-type does not contain a charset an accepted charset is selected, i.e. the method guarantees
   * that, if a content-type instance is returned within the option it will contain a charset.
   */
  def acceptableContentType(contentType: ContentType): Option[ContentType] = {
    if (isContentTypeAccepted(contentType)) Some {
      if (contentType.charset.isDefined) contentType
      else ContentType(contentType.mediaType, acceptedCharset)
    } else None
  }

  /**
   * Returns a charset that is accepted by the client.
   */
  def acceptedCharset: HttpCharset = {
    if (isCharsetAccepted(`ISO-8859-1`)) `ISO-8859-1`
    else acceptedCharsetRanges match {
      case (cs: HttpCharset) :: _ => cs
      case _ => throw new IllegalStateException // a HttpCharsetRange that is not `*` ?
    }
  }
  
}
