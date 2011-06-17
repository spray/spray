package cc.spray

import http._
import StatusCodes._
import utils.Logging

trait ErrorHandling {
  this: Logging =>
  
  protected[spray] def responseForException(request: HttpRequest, e: Exception): HttpResponse = {
    log.error(e, "Error during processing of request %s", request)
    e match {
      case e: HttpException => HttpResponse(e.failure, e.reason)
      case e: Exception => HttpResponse(InternalServerError, "Internal Server Error:\n" + e.toString)
    }
  }

}