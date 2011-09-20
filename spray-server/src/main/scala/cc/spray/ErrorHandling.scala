package cc.spray

import http._
import StatusCodes._
import utils.{IllegalResponseException, Logging}

trait ErrorHandling {
  this: Logging =>
  
  protected[spray] def responseForException(request: Any, e: Exception): HttpResponse = {
    log.error(e, "Error during processing of request %s", request)
    e match {
      case HttpException(failure, reason) => HttpResponse(failure, reason)
      case e: IllegalResponseException => throw e
      case e: Exception => HttpResponse(InternalServerError, "Internal Server Error:\n" + e.toString)
    }
  }

}