package cc.spray.http

import java.net.URI

case class HttpRequest(method: HttpMethod,
                       uri: String,
                       headers: List[HttpHeader],
                       parameters: Map[Symbol, String] = Map.empty,
                       content: Option[Array[Byte]] = None,
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

  def withUri(scheme: String = this.scheme,
              userInfo: String = this.userInfo,
              host: String = this.host,
              port: Int = this.port,
              path: String = this.path,
              query: String = this.query,
              fragment: String = this.fragment) = {
    copy(uri = new URI(scheme, userInfo, host, port, path, query, fragment).toString)
  }
  
  private def nonNull(s: String, default: String = ""): String = if (s == null) default else s
}
