package spray.io.openssl

import org.bridj.{BridJ, TypedPointer, Pointer}

import BridjedOpenssl._
import java.util.concurrent.locks.ReentrantLock

class OpenSSLException(msg: String) extends Exception(msg)

object OpenSSL {
  def apply(){}

  SSL_library_init()
  SSL_load_error_strings()
  val locks = {
    val num = CRYPTO_num_locks()
    println("Creating %d locks for openssl" format num)
    Array.fill(num)(new ReentrantLock)
  }
  val lockingCB = new LockingCB {
    def apply(mode: Int, `type`: Int, file: Pointer[java.lang.Byte], line: Int) {
      val lock = locks(`type`)
      if ((mode & 1) != 0)
        lock.lock()
      else
        lock.unlock()
    }
  }
  val threadIdCB = new ThreadIdCB {
    def apply(CRYPTO_THREADIDPtr1: Long) {
      CRYPTO_THREADID_set_numeric(CRYPTO_THREADIDPtr1, Thread.currentThread().getId)
    }
  }
  CRYPTO_set_locking_callback(lockingCB.toPointer)
  CRYPTO_THREADID_set_callback(threadIdCB.toPointer)

  def lastErrorString: String = {
    val resPtr = Pointer.allocateBytes(200)
    ERR_error_string_n(ERR_get_error(), resPtr, 200)
    resPtr.getCString
  }

  def checkResult(res: Int): Int =
    if (res <= 0) throw new OpenSSLException(lastErrorString)
    else res
  def checkResult(res: Long): Long =
    if (res <= 0) throw new OpenSSLException(lastErrorString)
    else res

  def checkResult[T <: TypedPointer](res: T): T =
    if (res == null) throw new OpenSSLException(lastErrorString)
    else res

  def shutdown() {
    BridJ.releaseAll()
  }
}
