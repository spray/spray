package cc.spray

import org.parboiled.common.FileUtils
import java.io.ByteArrayInputStream

object TimeOutHandler {
  
  type Handler = (RawRequest, RawResponse) => Unit
  
  val DefaultHandler: Handler = { (_, response) =>
    val bytes = "The server could not handle the request in the appropriate time frame (async timeout)".getBytes("ISO-8859-1")
    response.setStatus(500)
    response.addHeader("Async-Timeout", "expired")
    response.addHeader("Content-Type", "text/plain")
    response.addHeader("Content-Length", bytes.length.toString)
    if (Settings.CloseConnection) response.addHeader("Connection","close")
    FileUtils.copyAll(new ByteArrayInputStream(bytes), response.outputStream)
  } 
  
  private var handler: Handler = _
  
  def get = if (handler == null) DefaultHandler else handler
  
  def set(handler: Handler) { this.handler = handler }
}