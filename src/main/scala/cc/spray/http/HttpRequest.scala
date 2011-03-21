package cc.spray.http

import java.net.URI
import HttpHeaders._
import HttpVersions._
import parser.QueryParser

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
