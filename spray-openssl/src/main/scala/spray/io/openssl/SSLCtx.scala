package spray.io.openssl

import org.bridj.{Pointer, TypedPointer}
import spray.io.openssl.BridjedOpenssl._

class SSLCtx private[openssl](pointer: Long) extends TypedPointer(pointer) {
  def newSSL(): SSL = {
    val ssl = SSL_new(getPeer)
    require(ssl != 0L)
    new SSL(ssl)
  }

  def setDefaultVerifyPaths(): Int =
    SSL_CTX_set_default_verify_paths(getPeer)

  def setVerify(mode: Int) {
    SSL_CTX_set_verify(getPeer, mode, 0)
  }

  def setCipherList(ciphers: DirectBuffer): Int =
    SSL_CTX_set_cipher_list(getPeer, ciphers.pointer.getPeer)

  def usePrivateKeyFile(fileName: String, `type`: Int): Int = {
    val buffer = Pointer.allocateBytes(fileName.length + 1)
    buffer.setCString(fileName)
    SSL_CTX_use_PrivateKey_file(getPeer, buffer, `type`)
  }
  def useCertificateChainFile(fileName: String): Int = {
    val buffer = Pointer.allocateBytes(fileName.length + 1)
    buffer.setCString(fileName)
    SSL_CTX_use_certificate_chain_file(getPeer, buffer)
  }

  import SSL._
  def setOptions(options: Long): Long =
    SSL_CTX_ctrl(getPeer, SSL_CTRL_OPTIONS, options, 0)

  def setMode(mode: Long): Long =
    SSL_CTX_ctrl(getPeer, SSL_CTRL_MODE, mode, 0)

}
object SSLCtx {
  def create(method: Long): SSLCtx =
    new SSLCtx(SSL_CTX_new(method))

  // make sure openssl is initialized
  OpenSSL()
}
