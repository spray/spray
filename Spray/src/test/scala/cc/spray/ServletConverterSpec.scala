package cc.spray

import http.{HttpMethods, HttpRequest}
import org.specs.mock.Mockito
import org.specs.Specification
import scala.collection.JavaConversions._
import javax.servlet.http.HttpServletRequest
import javax.servlet.ServletInputStream
import http.HttpHeaders._
import http.MimeObjects._

class ServletConverterSpec extends Specification with Mockito {
  val convert = new ServletConverter {}
  
  "The ServletConverter" should {
    "propertly convert a minimal request" in (
      convert.toSprayRequest(Hsr("GET", "/path", Map()))
        mustEqual
      HttpRequest(HttpMethods.GET, "/path")
    )
    
    "propertly convert a request with headers" in (
      convert.toSprayRequest(Hsr("POST", "/path", Map("Accept" -> "text/html", "Content-Type" -> "text/plain")))
        mustEqual
      HttpRequest(HttpMethods.POST, "/path", List(Accept(`text/html`), `Content-Type`(`text/plain`)))
    )
  }
  
  private def Hsr(method: String, uri: String, headers: Map[String, String]) = {
    import java.util.Enumeration    
    make(mock[HttpServletRequest]) { hsr =>
      hsr.getMethod returns method
      hsr.getRequestURI returns uri
      hsr.getHeaderNames.asInstanceOf[Enumeration[String]] returns headers.keysIterator
      for ((key, value) <- headers) {
        hsr.getHeaders(key).asInstanceOf[Enumeration[String]] returns Iterator(value)
      }
      hsr.getInputStream returns make(mock[ServletInputStream]) { _.read(any) returns -1 }
      hsr.getRemoteAddr returns "xyz"
      hsr.getProtocol returns "HTTP/1.1"
    }
  }
  
}