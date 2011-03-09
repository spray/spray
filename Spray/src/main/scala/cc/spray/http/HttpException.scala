package cc.spray.http

case class HttpException(failure: HttpFailure, reason: String = "") extends RuntimeException(reason) {
  def status = HttpStatus(failure, reason)
}

object HttpException {
  def apply(failure: HttpFailure, cause: Throwable) = new HttpException(failure, cause.getMessage)
}