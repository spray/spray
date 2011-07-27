package cc.spray.client

import com.ning.http.client.{PerRequestConfig, RequestBuilder, ProxyServer}

case class RequestConfig(
  proxyServer: ProxyServer = null,
  requestTimeoutInMs: java.lang.Integer = null,
  followRedirects: java.lang.Boolean = null,
  realm: Realm = null
) {
  private[client] def configure(b: RequestBuilder) {
    if (proxyServer != null) b.setProxyServer(proxyServer)
    if (requestTimeoutInMs != null) b.setPerRequestConfig(new PerRequestConfig(null, requestTimeoutInMs.asInstanceOf[Int]))
    if (followRedirects != null) b.setFollowRedirects(followRedirects.asInstanceOf[Boolean])
    if (realm != null) b.setRealm(realm.toAhcRealm)
  }
}