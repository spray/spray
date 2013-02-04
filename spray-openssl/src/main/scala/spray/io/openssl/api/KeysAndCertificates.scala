package spray.io.openssl
package api

import org.bridj.TypedPointer
import java.security.Key
import java.security.cert.Certificate

import LibSSL._

class X509Certificate private[openssl](pointer: Long) extends TypedPointer(pointer)
object X509Certificate {
  def apply(cert: Certificate): X509Certificate = {
    require(cert.getType == "X.509", "Certificate must be of type X.509 but was '%s'" format cert.getType)

    BIO.withBytesBIO(cert.getEncoded)(d2i_X509_bio(_, 0).returnChecked)
  }
}

class EVP_PKEY private[openssl](pointer: Long) extends TypedPointer(pointer)
object EVP_PKEY {
  def apply(key: Key): EVP_PKEY = {
    require(key.getFormat == "PKCS#8", "Key must be of type PKCS8 but was '%s'" format key.getFormat)

    BIO.withBytesBIO(key.getEncoded) { bio =>
      EVP_PKCS82PKEY(d2i_PKCS8_PRIV_KEY_INFO_bio(bio, 0).returnChecked).returnChecked
    }
  }
}

class X509_STORE private[openssl](pointer: Long) extends TypedPointer(pointer) {
  def addCertificate(cert: X509Certificate): Unit =
    X509_STORE_add_cert(this, cert).returnChecked
}
