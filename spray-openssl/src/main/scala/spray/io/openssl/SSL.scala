package spray.io.openssl

import org.bridj.{JNI, TypedPointer}
import spray.io.openssl.BridjedOpenssl._

class SSL private[openssl](pointer: Long) extends TypedPointer(pointer) {
  def setBio(readBio: BIO, writeBio: BIO) {
    SSL_set_bio(getPeer, readBio.getPeer, writeBio.getPeer)
  }

  def connect(): Int =
    SSL_connect(getPeer)

  def accept(): Int =
    SSL_accept(getPeer)

  def setAcceptState(): Unit =
    SSL_set_accept_state(getPeer)


  def write(buffer: DirectBuffer, len: Int): Int = {
    require(buffer.size >= len)
    SSL_write(getPeer, buffer.address, len)
  }
  def read(buffer: DirectBuffer, len: Int): Int = {
    require(buffer.size >= len)
    SSL_read(getPeer, buffer.address, len)
  }

  def want: Int =
    SSL_want(getPeer)

  def pending: Int =
    SSL_pending(getPeer)

  def getError(ret: Int): Int =
    SSL_get_error(getPeer, ret)

  def free(): Unit = SSL_free(getPeer)

  def setCallback(f: (Int, Int) => Unit) {
    // FIXME: make sure the callback is not GC'd
    val cb = new InfoCallback {
      def apply(ssl: Long, where: Int, ret: Int): Unit = f(where, ret)
    }
    JNI.newGlobalRef(cb)
    SSL_set_info_callback(getPeer, cb.toPointer)
  }
}

object SSL {
  val SSL_CTRL_OPTIONS = 32
  val SSL_CTRL_MODE = 33

  val SSL_MODE_RELEASE_BUFFERS = 0x00000010L
  val SSL_OP_NO_COMPRESSION = 0x00020000L
}
