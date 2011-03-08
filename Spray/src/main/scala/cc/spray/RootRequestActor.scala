package cc.spray

import akka.util.Logging
import akka.actor.{PoisonPill, ActorRef, Actor}
import http.{HttpStatus, HttpRequest, HttpResponse}

class RootRequestActor(services: List[ActorRef], request: HttpRequest, respond: HttpResponse => Unit) extends Actor with Logging {
  private var responsesExpected = services.size
  private var responded = false
  
  override def preStart {
    if (services.isEmpty) {
      respond(noService)
      self ! PoisonPill
    } else {
      services.foreach(_ ! request)
    }
  }

  protected def receive = {
    case Some(response: HttpResponse) => {
      handleResponse(response)
      countReceive
    }
    case None => countReceive
  }
  
  private def handleResponse(response: HttpResponse) {
    if (responded) {
      log.slf4j.warn("Received a second response for request '{}':\n\nn{}\n\nIgnoring the additional response...", request, response)
    } else {
      respond(response)
      responded = true
    }
  }
  
  private def countReceive {
    responsesExpected -= 1
    if (responsesExpected == 0) {
      if (!responded) respond(noService)
      self ! PoisonPill
    }
  }
  
  private def noService: HttpResponse = {
    val msg = "No service available for [" + request.uri + "]"
    log.slf4j.debug(msg)
    HttpStatus(404, msg)
  }
  
}