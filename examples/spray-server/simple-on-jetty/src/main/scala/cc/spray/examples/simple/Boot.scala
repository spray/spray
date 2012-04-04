package cc.spray
package examples.simple

import akka.actor.{Props, ActorSystem}


// this class is instantiated by the servlet initializer,
// which also creates and shuts down the ActorSystem passed
// as an argument to this constructor
class Boot(system: ActorSystem) {

  val mainModule = new SimpleService {
    implicit def actorSystem = system
    // bake your module cake here
  }
  val service1 = system.actorOf(
    props = Props(new HttpService(mainModule.simpleService)),
    name = "service1"
  )
  val service2 = system.actorOf(
    props = Props(new HttpService(mainModule.secondService)),
    name = "service2"
  )
  val rootService = system.actorOf(
    props = Props(new RootService(service1, service2)),
    name = "spray-root-service" // must match the name in the config so the ConnectorServlet can find the actor
  )
  system.registerOnTermination {
    // put additional cleanup code clear
    system.log.info("Application shut down")
  }

}