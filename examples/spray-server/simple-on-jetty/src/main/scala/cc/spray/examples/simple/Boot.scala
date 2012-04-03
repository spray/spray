package cc.spray
package examples.simple

import util.Spray
import akka.actor.Props

class Boot {
  val mainModule = new SimpleService {
    // bake your module cake here
  }

  val service1 = Spray.system.actorOf(
    props = Props(new HttpService(mainModule.simpleService)),
    name = "service1"
  )
  val service2 = Spray.system.actorOf(
    props = Props(new HttpService(mainModule.secondService)),
    name = "service2"
  )
  val rootService = Spray.system.actorOf(
    props = Props(new RootService(service1, service2)),
    name = "spray-root-service" // must match the name in the config so the ConnectorServlet can find the actor
  )
}