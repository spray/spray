package cc.spray

import http._
import scala.collection.JavaConversions._
import org.apache.commons.io.IOUtils
import java.net.{UnknownHostException, InetAddress}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import HttpHeaders._
import MediaTypes._
import Charsets._

trait ServletConverter {
  
  protected[spray] def toSprayRequest(request: HttpServletRequest): HttpRequest = {
    val (ctHeaders, headers) = buildHeaders(request).partition(_.isInstanceOf[`Content-Type`])
    HttpRequest(
      HttpMethods.getForKey(request.getMethod).get,
      request.getRequestURI,
      headers,
      readContent(request, ctHeaders.headOption.asInstanceOf[Option[`Content-Type`]]),
      getRemoteHost(request),
      HttpVersions.getForKey(request.getProtocol)
    )
  }

  protected def buildHeaders(request: HttpServletRequest): List[HttpHeader] = {
    for (
      name <- request.getHeaderNames.asInstanceOf[java.util.Enumeration[String]].toList;
      value <- request.getHeaders(name).asInstanceOf[java.util.Enumeration[String]].toList
    ) yield {
      HttpHeader(name, value)
    }
  }

  protected def readContent(request: HttpServletRequest, header: Option[`Content-Type`]): Option[HttpContent] = {
    val bytes = IOUtils.toByteArray(request.getInputStream)
    if (bytes.length > 0) {
      // so far we do not guess the content-type
      // see http://www.w3.org/Protocols/rfc2616/rfc2616-sec7.html#sec7.2.1
      val contentType = header.map(_.contentType).getOrElse(ContentType(`application/octet-stream`))
      Some(HttpContent(contentType, bytes))
    } else {
      None
    }
  }
  
  protected def getRemoteHost(request: HttpServletRequest) = {
    try {
      Some(HttpIp(InetAddress.getByName(request.getRemoteAddr)))
    } catch {
      case _: UnknownHostException => None 
    }
  }
  
  protected[spray] def fromSprayResponse(response: HttpResponse): HttpServletResponse => Unit = {
    hsr => {
      hsr.setStatus(response.status.code.value)
      for (HttpHeader(name, value) <- response.headers) {
        if (name == "Content-Type") {
          // TODO: move higher up
          throw new RuntimeException("HttpResponse must not include explicit Content-Type header")
        }
        hsr.setHeader(name, value)
      }
      response.content match {
        case Some(buffer) => {
          hsr.setContentLength(buffer.length)
          hsr.setContentType(buffer.contentType.value)
          IOUtils.copy(buffer.inputStream, hsr.getOutputStream)
        }
        case None => if (!response.isSuccess) {
          hsr.setContentType("text/plain")
          hsr.getWriter.write(response.status.reason)
          hsr.getWriter.close()
        }
      }
      hsr.flushBuffer()
    }
  }
  
}