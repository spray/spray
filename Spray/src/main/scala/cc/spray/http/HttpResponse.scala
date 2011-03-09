package cc.spray.http

import HttpStatusCodes._
import HttpHeaders._

case class HttpResponse(status: HttpStatus = HttpStatus(OK),
                        headers: List[HttpHeader] = Nil,
                        content: HttpContent = NoContent) {

  def isSuccess: Boolean = status.code.isInstanceOf[HttpSuccess]
  
  def isWarning: Boolean = status.code.isInstanceOf[HttpWarning]
  
  def isFailure: Boolean = status.code.isInstanceOf[HttpFailure]
  
  def contentType: Option[MimeType] = (for (`Content-Type`(mimeType) <- headers) yield mimeType).headOption
}
