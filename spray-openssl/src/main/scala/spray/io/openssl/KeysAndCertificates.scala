package spray.io.openssl

import org.bridj.TypedPointer
import java.security.Key
import java.security.cert.Certificate

import BridjedOpenssl._
import OpenSSL._

class X509Certificate private[openssl](pointer: Long) extends TypedPointer(pointer) {

}
object X509Certificate {
  def apply(cert: Certificate): X509Certificate = {
    require(cert.getType == "X.509", "Certificate must be of type X.509 but was '%s'" format cert.getType)

    checkResult(d2i_X509_bio(BIO.fromBytes(cert.getEncoded), 0))
  }
}

class EVP_PKEY private[openssl](pointer: Long) extends TypedPointer(pointer) {

}
object EVP_PKEY {
  def apply(key: Key): EVP_PKEY = {
    require(key.getFormat == "PKCS#8", "Key must be of type PKCS8 but was '%s'" format key.getFormat)

    val pkcs8 = checkResult(d2i_PKCS8_PRIV_KEY_INFO_bio(BIO.fromBytes(key.getEncoded), 0))
    checkResult(EVP_PKCS82PKEY(pkcs8))
  }
}

class X509_STORE private[openssl](pointer: Long) extends TypedPointer(pointer) {
  def addCertificate(cert: X509Certificate): Unit =
    checkResult(X509_STORE_add_cert(this, cert))
}
