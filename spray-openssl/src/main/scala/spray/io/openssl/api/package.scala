package spray.io.openssl

import org.bridj.TypedPointer

package object api {
  implicit class IntResultWithReturnCheck(res: Int) {
    def returnChecked: Int = OpenSSL.checkResult(res)
  }
  implicit class LongResultWithReturnCheck(res: Long) {
    def returnChecked: Long = OpenSSL.checkResult(res)
  }
  implicit class PointerWithReturnCheck[T <: TypedPointer](res: T) {
    def returnChecked: T = OpenSSL.checkResult(res)
  }
}
