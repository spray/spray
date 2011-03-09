package cc.spray

import akka.util.Logging
import akka.actor.Actor
import http.{HttpRequest, HttpResponse, HttpStatus}

trait RoutingActor extends Actor with Logging {
  
  protected def responseForException(request: HttpRequest, e: Exception): HttpResponse = {
    log.error("Error during processing of request {}:\n{}", request, e)
    HttpResponse(HttpStatus(501, e.getMessage))
  }  
} 