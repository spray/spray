package spray.io.openssl.api

import spray.io.openssl.api.LibSSL._
import org.bridj.{JNI, Pointer}

/**
 * A native external data slot where managed data can be associated
 * with native data structures.
 */
trait ExDataSlot[T, E <: AnyRef] {
  def idx: Int
}

trait WithExDataMethods[T] {
  def setExData(idx: Int, arg: Long): Unit
  def getExData(idx: Int): Long

  def update[E <: AnyRef](slot: ExDataSlot[T, E], data: E): Unit = setExData(slot.idx, OpenSSL.createGlobalRef(data))
  def apply[E <: AnyRef](slot: ExDataSlot[T, E]): E = JNI.refToObject(getExData(slot.idx)).asInstanceOf[E]
}

trait WithExDataCompanion[T] {
  def newExDataIndex: (Long, Long, Long, Long, Pointer[CRYPTO_EX_free]) => Int

  def createExDataSlot[E <: AnyRef](): ExDataSlot[T, E] =
    new ExDataSlot[T, E] {
      val idx = newExDataIndex(0, 0, 0, 0, OpenSSL.exDataFree.toPointer)
    }
}
