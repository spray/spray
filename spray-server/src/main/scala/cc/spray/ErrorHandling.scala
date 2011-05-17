package cc.spray

import http._
import StatusCodes._
import java.io.{PrintWriter, StringWriter}
import utils.Logging

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
    log.error(e, "Error during processing of request %s", request)
  }
  
}