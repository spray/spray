package spray.io.openssl

import org.bridj.TypedPointer

package object api {
  class IntResultWithReturnCheck(res: Int) {
    def returnChecked: Int = OpenSSL.checkResult(res)
  }
  class LongResultWithReturnCheck(res: Long) {
    def returnChecked: Long = OpenSSL.checkResult(res)
  }
  class PointerWithReturnCheck[T <: TypedPointer](res: T) {
    def returnChecked: T = OpenSSL.checkResult(res)
  }

  implicit def checkedInt(res: Int): IntResultWithReturnCheck = new IntResultWithReturnCheck(res)
  implicit def checkedLong(res: Long): LongResultWithReturnCheck = new LongResultWithReturnCheck(res)
  implicit def checkedPointer[T <: TypedPointer](res: T): PointerWithReturnCheck[T] = new PointerWithReturnCheck(res)
}
