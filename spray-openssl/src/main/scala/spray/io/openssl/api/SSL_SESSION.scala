package spray.io.openssl.api

import org.bridj.{Pointer, TypedPointer}

class SSL_SESSION private[openssl](pointer: Long) extends TypedPointer(pointer) {
  def toBytes: Array[Byte] = {
    val length = LibSSL.i2d_SSL_SESSION(this, OpenSSL.NULL)
    val buffer = Pointer.allocateBytes(length)
    val ps = Pointer.allocatePointers(classOf[java.lang.Byte], 1)
    ps.set(0, buffer)
    val len = LibSSL.i2d_SSL_SESSION(this, ps)
    assert(len == length)
    buffer.getBytes(len)
  }
}

object SSL_SESSION {
  def fromBytes(bytes: Array[Byte]): SSL_SESSION = {
    val buffer = Pointer.allocateBytes(bytes.length)
    val ps = Pointer.allocatePointers(classOf[java.lang.Byte], 1)
    ps.set(0, buffer)
    buffer.setBytes(bytes)
    LibSSL.d2i_SSL_SESSION(0, ps, bytes.length)
  }
}
