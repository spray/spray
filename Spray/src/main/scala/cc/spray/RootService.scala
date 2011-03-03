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
    case rm: RequestMethod => fire(Context(toSprayRequest(rm.request), respond(rm)))

    case Attach(service) => attach(service)
  }

  protected def fire(context: Context) {
    if (services.isEmpty) {
      noService(context)
    } else {
      val futures = services.map { service =>
        (service !!! context).asInstanceOf[Future[RoutingResult]]
      }
      Futures.reduce(futures) {
        (acc, result) => if (acc == Handled) acc else result
      } onComplete { f =>
        if (f.result.get == NotHandled) noService(context)
      }
    }
  }
  
  protected def respond(rm: RequestMethod): HttpResponse => Unit = {
    response => rm.rawComplete(fromSprayResponse(response))    
  }

  protected def noService(context: Context) {
    val msg = "No service available for [" + context.request.uri + "]"
    log.slf4j.debug(msg)
    context.respond(HttpStatus(404, msg))
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