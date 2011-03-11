package cc.spray.http

import HttpStatusCodes._

case class HttpResponse(status: HttpStatus = OK,
                        headers: List[HttpHeader] = Nil,
                        content: HttpContent = EmptyContent) {

  def isSuccess: Boolean = status.code.isInstanceOf[HttpSuccess]
  
  def isWarning: Boolean = status.code.isInstanceOf[HttpWarning]
  
  def isFailure: Boolean = status.code.isInstanceOf[HttpFailure]
  
}
