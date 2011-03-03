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

      val msg = if (handled) "Handled {}" else "Did not handle {}" 
      log.slf4j.debug(msg, context.request)
    }
  }
}

object HttpService {
  def apply(mainRoute: Route) = new HttpService(mainRoute) 
}