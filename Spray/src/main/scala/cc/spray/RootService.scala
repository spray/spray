package cc.spray

import akka.http._
import akka.actor.{Actor, ActorRef}
import akka.util.Logging
import akka.dispatch.{Futures, Future}
import http._

class RootService extends Actor with ServletConverter with Logging {
  protected var services: List[ActorRef] = Nil

  self.id = "spray-root-service"

  override protected[spray] def receive = {
    case rm: RequestMethod => fire(Context(toSprayRequest(rm.request), responder(rm)))

    case Attach(service) => attach(service)
  }

  protected def fire(context: Context) {
    if (services.isEmpty) {
      noService(context)
    } else {
      val futures = services.map { service =>
        (service !!! context).asInstanceOf[Future[Boolean]]
      }
      Futures
        .reduce(futures)(_ || _)
        .onComplete(fut => if (!fut.result.get) noService(context))
    }
  }
  
  protected def responder(rm: RequestMethod): HttpResponse => Unit = {
    response => rm.rawComplete(fromSprayResponse(response))    
  }

  protected def noService(context: Context) {
    val msg = "No service available for [" + context.request.uri + "]"
    log.slf4j.debug(msg)
    context.responder(HttpStatus(404, msg))
  }
  
  protected def attach(serviceActor: ActorRef) {
    services = serviceActor :: services
    if (serviceActor.isUnstarted) serviceActor.start
  }
}

case class Attach(serviceActorRef: ActorRef)

object Attach {
  def apply(serviceActor: => Actor): Attach = apply(Actor.actorOf(serviceActor))
}