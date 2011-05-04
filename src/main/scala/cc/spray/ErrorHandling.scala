package cc.spray

import http._
import StatusCodes._
import java.io.{PrintWriter, StringWriter}
import akka.util.Logging

trait ErrorHandling {
  
  protected[spray] def responseForException(request: HttpRequest, e: Exception): HttpResponse = {
    logException(request, e)
    e match {
      case e: HttpException => HttpResponse(e.failure, e.reason)
      case e: Exception => HttpResponse(InternalServerError, e.toString)
    }
  }
  
  protected def logException(request: HttpRequest, e: Exception)
  
}

trait ErrorLogging extends ErrorHandling {
  this: Logging =>
  
  protected def logException(request: HttpRequest, e: Exception) {
    val stackTrace = new StringWriter()
    e.printStackTrace(new PrintWriter(stackTrace));
    log.slf4j.error("Error during processing of request {}:\n{}", request, stackTrace)
  }
  
}