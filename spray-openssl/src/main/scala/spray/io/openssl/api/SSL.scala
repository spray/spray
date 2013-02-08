package spray.io.openssl
package api

import org.bridj.{Pointer, JNI, TypedPointer}
import LibSSL._

class SSL private[openssl](pointer: Long) extends TypedPointer(pointer) with WithExDataMethods[SSL] {
  def setBio(readBio: BIO, writeBio: BIO): Unit = SSL_set_bio(getPeer, readBio.getPeer, writeBio.getPeer)
  def connect(): Int = SSL_connect(getPeer)
  def accept(): Int = SSL_accept(getPeer)
  def setAcceptState(): Unit = SSL_set_accept_state(getPeer)

  def getCtx: SSLCtx = SSL_get_SSL_CTX(this)

  def write(buffer: DirectBuffer, len: Int): Int = {
    require(buffer.size >= len)
    SSL_write(getPeer, buffer.address, len)
  }
  def read(buffer: DirectBuffer, len: Int): Int = {
    require(buffer.size >= len)
    SSL_read(getPeer, buffer.address, len)
  }

  def want: Int = SSL_want(getPeer)
  def pending: Int = SSL_pending(getPeer)

  def get1Session(): SSL_SESSION = SSL_get1_session(getPeer).returnChecked
  def setSession(session: SSL_SESSION): Unit = SSL_set_session(this, session).returnChecked

  def getError(ret: Int): Int =
    SSL_get_error(getPeer, ret)

  def setExData(idx: Int, arg: Long): Unit = SSL_set_ex_data(getPeer, idx, arg).returnChecked
  def getExData(idx: Int): Long = SSL_get_ex_data(getPeer, idx)

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

object SSL extends WithExDataCompanion[SSL] {
  val SSL_CTRL_OPTIONS = 32
  val SSL_CTRL_MODE = 33
  val SSL_CTRL_SET_SESS_CACHE_MODE = 44

  val SSL_MODE_RELEASE_BUFFERS = 0x00000010L
  val SSL_OP_NO_COMPRESSION = 0x00020000L

  def newExDataIndex: (Long, Long, Long, Long, Pointer[CRYPTO_EX_free]) => Int = LibSSL.SSL_get_ex_new_index
}
