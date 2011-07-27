package cc.spray.client

import com.ning.http.client.{Realm => _, _}
import filter.{IOExceptionFilter, ResponseFilter, RequestFilter}
import javax.net.ssl.SSLContext
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}

case class ClientConfig(
  limits: Limits = null,
  connectionTimeOutInMs: java.lang.Integer = null,
  idleConnectionInPoolTimeoutInMs: java.lang.Integer = null,
  requestTimeoutInMs: java.lang.Integer = null,
  redirectEnabled: java.lang.Boolean = null,
  compressionEnabled: java.lang.Boolean = null,
  userAgent: String = null,
  allowConnectionPooling: java.lang.Boolean = null,
  reaper: ScheduledExecutorService = null,
  applicationThreadPool: ExecutorService = null,
  proxyServer: ProxyServer = null,
  sslConfig: SslConfig = null,
  providerConfig: AsyncHttpProviderConfig[_, _] = null,
  connectionsPool: ConnectionsPool[_, _] = null,
  realm: Realm = null,
  requestFilters: List[RequestFilter] = Nil,
  responseFilters: List[ResponseFilter] = Nil,
  ioExceptionFilters: List[IOExceptionFilter] = Nil,
  requestCompressionLevel: java.lang.Integer = null,
  useRawUrl: java.lang.Boolean = null
) {
  def toAhcConfig = {
    val b = new AsyncHttpClientConfig.Builder()
    if (limits != null) limits.configure(b)
    if (connectionTimeOutInMs != null) b.setConnectionTimeoutInMs(connectionTimeOutInMs.asInstanceOf[Int])
    if (idleConnectionInPoolTimeoutInMs != null) b.setIdleConnectionInPoolTimeoutInMs(idleConnectionInPoolTimeoutInMs.asInstanceOf[Int])
    if (requestTimeoutInMs != null) b.setRequestTimeoutInMs(requestTimeoutInMs.asInstanceOf[Int])
    if (redirectEnabled != null) b.setFollowRedirects(redirectEnabled.asInstanceOf[Boolean])
    if (compressionEnabled != null) b.setCompressionEnabled(compressionEnabled.asInstanceOf[Boolean])
    if (userAgent != null) b.setUserAgent(userAgent)
    if (allowConnectionPooling != null) b.setAllowPoolingConnection(allowConnectionPooling.asInstanceOf[Boolean])
    if (reaper != null) b.setScheduledExecutorService(reaper)
    if (applicationThreadPool != null) b.setExecutorService(applicationThreadPool)
    if (proxyServer != null) b.setProxyServer(proxyServer)
    if (sslConfig != null) sslConfig.configure(b)
    if (providerConfig != null) b.setAsyncHttpClientProviderConfig(providerConfig)
    if (connectionsPool != null) b.setConnectionsPool(connectionsPool)
    if (realm != null) b.setRealm(realm.toAhcRealm)
    requestFilters.foreach(b.addRequestFilter(_))
    responseFilters.foreach(b.addResponseFilter(_))
    ioExceptionFilters.foreach(b.addIOExceptionFilter(_))
    if (requestCompressionLevel != null) b.setRequestCompressionLevel(requestCompressionLevel.asInstanceOf[Int])
    if (useRawUrl != null) b.setUseRawUrl(useRawUrl.asInstanceOf[Boolean])
    b.build()
  }
}

case class Limits(
  maxTotalConnections: java.lang.Integer = null,
  maxConnectionsPerHost: java.lang.Integer = null,
  maxRedirects: java.lang.Integer = null,
  maxRequestRetries: java.lang.Integer = null
) {
  private[client] def configure(b: AsyncHttpClientConfig.Builder) {
    if (maxTotalConnections != null) b.setMaximumConnectionsTotal(maxTotalConnections.asInstanceOf[Int])
    if (maxConnectionsPerHost != null) b.setMaximumConnectionsPerHost(maxConnectionsPerHost.asInstanceOf[Int])
    if (maxRedirects != null) b.setMaximumNumberOfRedirects(maxRedirects.asInstanceOf[Int])
    if (maxRequestRetries != null) b.setMaxRequestRetry(maxRequestRetries.asInstanceOf[Int])
  }
}

case class SslConfig(
  context: SSLContext = null,
  engineFactory: SSLEngineFactory = null,
  allowConnectionPooling: java.lang.Boolean = null
) {
  private[client] def configure(b: AsyncHttpClientConfig.Builder) {
    if (context != null) b.setSSLContext(context)
    if (engineFactory != null) b.setSSLEngineFactory(engineFactory)
    if (allowConnectionPooling != null) b.setAllowSslConnectionPool(allowConnectionPooling.asInstanceOf[Boolean])
  }
}