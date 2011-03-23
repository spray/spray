package cc.spray.http

import HttpStatusCodes._

case class HttpResponse(status: HttpStatus = OK,
                        headers: List[HttpHeader] = Nil,
                        content: Option[HttpContent] = None) {

  def isSuccess: Boolean = status.code.isInstanceOf[HttpSuccess]
  
  def isWarning: Boolean = status.code.isInstanceOf[HttpWarning]
  
  def isFailure: Boolean = status.code.isInstanceOf[HttpFailure]
  
  def withContentTransformed(f: HttpContent => HttpContent): HttpResponse = copy(content = content.map(f))
}
