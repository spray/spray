package spray.io.openssl
package api

import java.util.concurrent.locks.ReentrantLock

import org.bridj.{JNI, BridJ, TypedPointer, Pointer}

import LibSSL._

class OpenSSLException(msg: String) extends Exception(msg)

object OpenSSL {
  def apply(){}

  SSL_library_init()
  SSL_load_error_strings()
  val locks = Seq.fill(CRYPTO_num_locks())(new ReentrantLock)
  val lockingCB = new LockingCB {
    def apply(mode: Int, `type`: Int, file: Pointer[java.lang.Byte], line: Int) {
      val lock = locks(`type`)
      if ((mode & 1) != 0) lock.lock()
      else lock.unlock()
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

  def SSLv23_method = LibSSL.SSLv23_method()

  def checkResult(res: Int): Int =
    if (res <= 0) throw new OpenSSLException(lastErrorString)
    else res
  def checkResult(res: Long): Long =
    if (res <= 0) throw new OpenSSLException(lastErrorString)
    else res

  def checkResult[T <: TypedPointer](res: T): T =
    if (res == null) throw new OpenSSLException(lastErrorString)
    else res

  def NULL[T]: Pointer[T] = Pointer.NULL.asInstanceOf[Pointer[T]]

  private[this] var shutdownActions: List[() => Unit] = Nil
  /** Register a block of code to be run when OpenSSL processing is shutdown */
  def registerShutdownAction(body: => Unit): Unit = synchronized {
    shutdownActions ::= (body _)
  }

  private[this] var globalRefs: List[Long] = Nil
  /**
   * Allow creation of managed global references that will be collected when `shutdown()` is called.
   */
  def createGlobalRef(obj: AnyRef): Long = synchronized {
    val ref = JNI.newGlobalRef(obj)
    globalRefs ::= ref
    ref
  }
  def removeGlobalRef(ref: Long): Unit = synchronized {
    JNI.deleteGlobalRef(ref)
    globalRefs = globalRefs.filterNot(_ == ref)
  }

  registerShutdownAction {
    if (globalRefs.nonEmpty) {
      println("%d global refs still alive" format globalRefs.size)

      globalRefs.foreach(JNI.deleteGlobalRef)
      globalRefs = Nil
    }
  }

  /** Shutdown and cleanup the native bindings for OpenSSL. Don't use any
   *  classes in this package any more afterwards.
   */
  def shutdown(): Unit = synchronized {
    BridJ.releaseAll()

    shutdownActions.foreach(_())
    shutdownActions = Nil
  }
}
