package cc.spray

import akka.http._
import akka.actor.{Actor, ActorRef}
import akka.util.Logging
import http._

class RootService extends Actor with ServletConverter with Logging {
  protected var services: List[ActorRef] = Nil

  self.id = "spray-root-service"

  override protected[spray] def receive = {
    case rm: RequestMethod => {
      val rootRequestActor = createRootRequestActor(toSprayRequest(rm.request), respond(rm))
      Actor.actorOf(rootRequestActor).start
    }

    case Attach(service) => attach(service)
  }
  
  protected def createRootRequestActor(request: HttpRequest, responder: HttpResponse => Unit) = {
    new RootRequestActor(services, request, responder)
  }

  protected def respond(rm: RequestMethod)(response: HttpResponse) {
    rm.rawComplete(fromSprayResponse(response))
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