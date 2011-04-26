package cc.spray

import org.parboiled.common.FileUtils
import java.io.ByteArrayInputStream

object TimeOutHandler {
  
  val DefaultHandler: TimeOutHandler = { (_, response) =>
    val bytes = "The asynchronous request processing has timed out".getBytes("ISO-8859-1")
    response.setStatus(500)
    response.addHeader("Async-Timeout", "expired")
    response.addHeader("Content-Type", "text/plain")
    response.addHeader("Content-Length", bytes.length.toString)
    if (Settings.CloseConnection) response.addHeader("Connection","close")
    FileUtils.copyAll(new ByteArrayInputStream(bytes), response.outputStream)
  } 
  
  private var handler: TimeOutHandler = _
  
  def get = if (handler == null) DefaultHandler else handler
  
  def set(handler: TimeOutHandler) { this.handler = handler }
}