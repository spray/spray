package cc.spray.http

import HttpStatusCodes._
import java.util.Arrays

case class HttpResponse(status: HttpStatus = HttpStatus(OK),
                        headers: List[HttpHeader] = Nil,
                        content: Option[Array[Byte]] = None) {

  override def hashCode = 31 * (status.hashCode + (31 * headers.hashCode)) + {
    if (content.isEmpty) 0 else Arrays.hashCode(content.get)  
  }

  override def equals(obj: Any) = obj match {
    case o: HttpResponse => status == o.status && headers == o.headers && {
      if (content.isEmpty) {
        o.content.isEmpty
      } else {
        o.content.isDefined && Arrays.equals(content.get, o.content.get)
      } 
    }
    case _ => false
  }  
}
