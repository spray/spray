package cc.spray

import akka.http.Endpoint
import akka.actor.Actor
import akka.util.Logging

class HttpService(val mainRoute: Route) extends Actor with Logging {

  // use the configurable dispatcher
  self.dispatcher = Endpoint.Dispatcher 

  protected def receive = {
    case context: Context => {
      val handled = mainRoute(context)

      self.reply(handled) // inform the root service

      handled match {
        case Handled => log.slf4j.debug("Handled {}", context.request)
        case NotHandled => log.slf4j.debug("Did not handle {}", context.request)
      }
    }

    case unknown => log.slf4j.error("Received unexpected message: {}", unknown)
  }

}

object HttpService {
  def apply(mainRoute: Route) = new HttpService(mainRoute) 
}