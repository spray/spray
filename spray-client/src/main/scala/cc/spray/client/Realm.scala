package cc.spray.client

import com.ning.http.client.{Realm => AhcRealm}

case class Realm(
  principal: String = null,
  password: String = null,
  scheme: AhcRealm.AuthScheme = null,
  realmName: String = null,
  nonce: String = null,
  algorithm: String = null,
  response: String = null,
  qop: String = null,
  nc: String = null,
  uri: String = null,
  methodName: String = null,
  usePreemptive: java.lang.Boolean = null,
  ntlmDomain: String = null,
  enc: String = null,
  ntlmHost: String = null,
  ntlmMessageType2Received: java.lang.Boolean = null
) {
  
  def toAhcRealm = {
    val b = new AhcRealm.RealmBuilder()
    if (principal != null) b.setPrincipal(principal)
    if (password != null) b.setPassword(password)
    if (scheme != null) b.setScheme(scheme)
    if (realmName != null) b.setRealmName(realmName)
    if (nonce != null) b.setNonce(nonce)
    if (algorithm != null) b.setAlgorithm(algorithm)
    if (response != null) b.setResponse(response)
    if (qop != null) b.setQop(qop)
    if (nc != null) b.setNc(nc)
    if (uri != null) b.setUri(uri)
    if (methodName != null) b.setMethodName(uri)
    if (usePreemptive != null) b.setUsePreemptiveAuth(usePreemptive.asInstanceOf[Boolean])
    if (ntlmDomain != null) b.setNtlmDomain(ntlmDomain)
    if (enc != null) b.setEnconding(enc)
    if (ntlmHost != null) b.setNtlmHost(ntlmHost)
    if (ntlmMessageType2Received != null) b.setNtlmMessageType2Received(ntlmMessageType2Received.asInstanceOf[Boolean])
    b.build()
  }
  
}