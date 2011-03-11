package cc.spray.http

import java.net.URI
import HttpHeaders._
import HttpStatusCodes._

case class HttpRequest(method: HttpMethod,
                       uri: String = "",
                       headers: List[HttpHeader] = Nil,
                       parameters: Map[Symbol, String] = Map.empty,
                       content: HttpContent = EmptyContent,
                       remoteHost: Option[HttpIp] = None,
                       version: Option[HttpVersion] = Some(HttpVersions.`HTTP/1.1`)) {
  
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
  
  lazy val acceptedMimeTypes: List[MimeType] = {
    // TODO: sort by preference
    for (Accept(mimeTypes) <- headers; mType <- mimeTypes) yield mType
  }
  
  lazy val acceptedCharsets: List[Charset] = {
    // TODO: sort by preference
    for (`Accept-Charset`(charsets) <- headers; cs <- charsets) yield cs
  }

  def isContentTypeAccepted(contentType: ContentType) = {
    isMimeTypeAccepted(contentType.mimeType) && isCharsetAccepted(contentType.charset)  
  } 
  
  def isMimeTypeAccepted(mimeType: MimeType) = {
    // according to the HTTP spec a client has to accept all mime types if no Accept header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1
    acceptedMimeTypes.isEmpty || acceptedMimeTypes.exists(_.equalsOrIncludes(mimeType))
  }
  
  def isCharsetAccepted(charset: Charset) = {
    // according to the HTTP spec a client has to accept all charsets if no Accept-Charset header is sent with the request
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.2
    acceptedCharsets.isEmpty || acceptedCharsets.exists(_.equalsOrIncludes(charset))
  }
}
