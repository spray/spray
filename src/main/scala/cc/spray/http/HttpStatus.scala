package cc.spray
package http

case class HttpStatus(code: HttpStatusCode, unsafeReason: String = "") {
  val reason = {
    if (unsafeReason.isEmpty)
      code.defaultMessage
    else
      unsafeReason.replace('\r', ' ').replace('\n', ' ')
  }
}

object HttpStatus {
  implicit def statusCode2HttpStatus(code: HttpStatusCode): HttpStatus = HttpStatus(code)
  
  implicit def httpStatus2HttpResponse(status: HttpStatus): HttpResponse = HttpResponse(status)
}