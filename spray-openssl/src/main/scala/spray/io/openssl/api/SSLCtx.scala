package spray.io.openssl
package api

import org.bridj.{Pointer, TypedPointer}
import LibSSL._

class SSLCtx private[openssl](pointer: Long) extends TypedPointer(pointer) with WithExDataMethods[SSLCtx] {
  def newSSL(): SSL = {
    val ssl = SSL_new(getPeer).returnChecked
    require(ssl != 0L)
    new SSL(ssl)
  }

  def setDefaultVerifyPaths(): Unit =
    SSL_CTX_set_default_verify_paths(getPeer).returnChecked

  def setVerify(mode: Int) {
    SSL_CTX_set_verify(getPeer, mode, 0)
  }

  def setCipherList(ciphers: DirectBuffer): Unit =
    SSL_CTX_set_cipher_list(getPeer, ciphers.pointer.getPeer).returnChecked

  def usePrivateKeyFile(fileName: String, `type`: Int): Unit = {
    val buffer = Pointer.allocateBytes(fileName.length + 1)
    buffer.setCString(fileName)
    SSL_CTX_use_PrivateKey_file(getPeer, buffer, `type`).returnChecked
  }
  def useCertificateChainFile(fileName: String): Unit = {
    val buffer = Pointer.allocateBytes(fileName.length + 1)
    buffer.setCString(fileName)
    SSL_CTX_use_certificate_chain_file(getPeer, buffer).returnChecked
  }

  import SSL._
  def setOptions(options: Long): Long = SSL_CTX_ctrl(getPeer, SSL_CTRL_OPTIONS, options, 0)
  def setMode(mode: Long): Long = SSL_CTX_ctrl(getPeer, SSL_CTRL_MODE, mode, 0)
  def getCertificateStore: X509_STORE = SSL_CTX_get_cert_store(getPeer)

  def setExData(idx: Int, arg: Long): Unit = SSL_CTX_set_ex_data(getPeer, idx, arg)
  def getExData(idx: Int): Long = SSL_CTX_get_ex_data(getPeer, idx)

  def setSessionCacheMode(mode: Long): Long = SSL_CTX_ctrl(getPeer, SSL_CTRL_SET_SESS_CACHE_MODE, mode, 0)

  var sessionCallbackRef: Option[Long] = None
  OpenSSL.registerShutdownAction {
    synchronized {
      sessionCallbackRef.foreach(OpenSSL.removeGlobalRef)
      sessionCallbackRef = None
    }
  }
  def setNewSessionCallback(callback: NewSessionCB): Unit = synchronized {
    val newRef = OpenSSL.createGlobalRef(callback)
    SSL_CTX_sess_set_new_cb(getPeer, callback.toPointer)

    sessionCallbackRef.foreach(OpenSSL.removeGlobalRef)
    sessionCallbackRef = Some(newRef)
  }
}
object SSLCtx extends WithExDataCompanion[SSLCtx] {
  // make sure openssl is initialized
  OpenSSL()

  val SSL_SESS_CACHE_OFF = 0x0000
  val SSL_SESS_CACHE_CLIENT = 0x0001
  val SSL_SESS_CACHE_SERVER	= 0x0002
  val SSL_SESS_CACHE_BOTH	 = SSL_SESS_CACHE_CLIENT | SSL_SESS_CACHE_SERVER
  val SSL_SESS_CACHE_NO_AUTO_CLEAR = 0x0080
  val SSL_SESS_CACHE_NO_INTERNAL_LOOKUP = 0x0100
  val SSL_SESS_CACHE_NO_INTERNAL_STORE = 0x0200
  val SSL_SESS_CACHE_NO_INTERNAL = SSL_SESS_CACHE_NO_INTERNAL_LOOKUP | SSL_SESS_CACHE_NO_INTERNAL_STORE

  def create(method: Long): SSLCtx = new SSLCtx(SSL_CTX_new(method).returnChecked)

  def newExDataIndex: (Long, Long, Long, Long, Pointer[CRYPTO_EX_free]) => Int = SSL_CTX_get_ex_new_index
}
