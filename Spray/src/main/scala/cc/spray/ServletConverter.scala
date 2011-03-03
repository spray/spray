package cc.spray

import http._
import scala.collection.JavaConversions._
import org.apache.commons.io.IOUtils
import java.net.{UnknownHostException, InetAddress}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import java.io.ByteArrayInputStream
import akka.util.Logging

trait ServletConverter {
  this: Logging =>
  
  protected def toSprayRequest(request: HttpServletRequest): HttpRequest = {
    val headers = buildHeaders(request)
    HttpRequest(
      HttpMethods.get(request.getMethod).get,
      request.getRequestURI,
      headers,
      buildParameters(request.getParameterMap.asInstanceOf[java.util.Map[String, Array[String]]]),
      readContent(request, headers),
      getRemoteHost(request),
      HttpVersions.get(request.getProtocol)
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

  protected def buildParameters(parameterMap: java.util.Map[String, Array[String]]) = {
    (Map.empty[Symbol, String] /: parameterMap) {
      (map, entry) => {
        map.updated(Symbol(entry._1), if (entry._2.isEmpty) "" else entry._2(0))
      }
    }
  }
  
  protected def readContent(request: HttpServletRequest, headers: List[HttpHeader]): Option[Array[Byte]] = {
    val buffer = IOUtils.toByteArray(request.getInputStream)
    if (buffer.length > 0) Some(buffer) else None
  }
  
  protected def getRemoteHost(request: HttpServletRequest) = {
    try {
      Some(HttpIp(InetAddress.getByName(request.getRemoteAddr)))
    } catch {
      case _: UnknownHostException => None 
    }
  }
  
  protected def fromSprayResponse(response: HttpResponse): HttpServletResponse => Unit = {
    hsr => {
      hsr.setStatus(response.status.code.value)
      for (header <- response.headers) header match {
        case HttpHeaders.`Content-Type`(mimeType) => {
          log.slf4j.warn("Explicitly set Content-Type response header with value '{}' will be ignored!", mimeType)
        }
        case HttpHeader(name, value) => hsr.setHeader(name, value)
      }
      response.content match {
        case Some(buffer) => {
          IOUtils.copy(new ByteArrayInputStream(buffer), hsr.getOutputStream)
          hsr.setContentLength(buffer.length)
        }
        case None => {
          hsr.setContentType("text/plain")
          hsr.getWriter.write(response.status.reason)
          hsr.getWriter.close
        }
      }
      hsr.flushBuffer
    }
  }
  
}