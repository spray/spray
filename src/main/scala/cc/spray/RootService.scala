package cc.spray

import akka.http._
import akka.actor.{Actor, ActorRef}
import akka.util.Logging
import http._
import akka.dispatch.{Future, Futures}
import HttpStatusCodes._

class RootService extends Actor with ServletConverter with Logging {
  private var services: List[ActorRef] = Nil
  private var handler: RequestMethod => Unit = handleNoServices  

  self.id = "spray-root-service"

  protected def receive = {
    case rm: RequestMethod => {
      try {
        handler(rm)
      } catch {
        case e: Exception => handleException(e, rm)
      }
    }
    case Attach(service) => attach(service)
    case Detach(service) => detach(service)
  }
  
  private def handleNoServices(rm: RequestMethod) {
    rm.rawComplete(fromSprayResponse(noService(rm.request.getRequestURI)))
  }
  
  private def handleOneService(rm: RequestMethod) {
    (services.head !!! toSprayRequest(rm.request)) onComplete completeRequest(rm) _
  }
  
  private def handleMultipleServices(rm: RequestMethod) {
    val request = toSprayRequest(rm.request) 
    val futures = services.map(_ !!! request).asInstanceOf[List[Future[Any]]]
    Futures.fold(None.asInstanceOf[Option[Any]])(futures) { (result, future) =>
      (result, future) match {
        case (None, None) => None
        case (None, x@ Some(_: HttpResponse)) => x
        case (x@ Some(_: HttpResponse), None) => x 
        case (x@ Some(_: HttpResponse), Some(y: HttpResponse)) => {
          log.slf4j.warn("Received a second response for request '{}':\n\nn{}\n\nIgnoring the additional response...", request, y)
          x
        }
      }
    } onComplete completeRequest(rm) _
  }
  
  protected def handleException(e: Exception, rm: RequestMethod) {
    rm.rawComplete(fromSprayResponse( e match {
      case e: HttpException => HttpResponse(e.status)
      case e: Exception => HttpResponse(HttpStatus(InternalServerError, e.getMessage))
    })) 
  }
  
  private def completeRequest(rm: RequestMethod)(future: Future[Option[Any]]) {
    val problem = future.exception 
    if (problem.isEmpty) {
      future.result.get match {
        case Some(response: HttpResponse) => rm.rawComplete(fromSprayResponse(response))
        case None => handleNoServices(rm)
      }
    } else {
      log.slf4j.error("Exception during request processing: {}", problem.get)
    }
  }
  
  protected def attach(serviceActor: ActorRef) {
    services = serviceActor :: services
    if (serviceActor.isUnstarted) serviceActor.start
    updateHandler
  }
  
  protected def detach(serviceActor: ActorRef) {
    services = services.filter(_ != serviceActor)    
    updateHandler
  }
  
  private def updateHandler {
    handler = services.size match {
      case 0 => handleNoServices
      case 1 => handleOneService
      case _ => handleMultipleServices
    }
  }
  
  protected def noService(uri: String): HttpResponse = {
    val msg = "No service available for [" + uri + "]"
    log.slf4j.debug(msg)
    HttpStatus(404, msg)
  }
}

case class Attach(serviceActorRef: ActorRef)

case class Detach(serviceActorRef: ActorRef)

object Attach {
  def apply(serviceActor: => Actor): Attach = apply(Actor.actorOf(serviceActor))
}