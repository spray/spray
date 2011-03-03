package cc.spray.http

import HttpStatusCodes._

case class HttpResponse(status: HttpStatus = HttpStatus(OK),
                        headers: List[HttpHeader] = Nil,
                        content: Option[Array[Byte]] = None)