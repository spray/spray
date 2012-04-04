package cc.spray
package examples.stopwatch

import akka.actor.{ActorSystem, Props}

// this class is instantiated by the servlet initializer,
// which also creates and shuts down the ActorSystem passed
// as an argument to this constructor
class Boot(system: ActorSystem) {

  val mainModule = new StopWatchService {
    implicit def actorSystem = system
    // bake your module cake here
  }
  val httpService = system.actorOf(
    props = Props(new HttpService(mainModule.stopWatchService)),
    name = "my-service"
  )
  val rootService = system.actorOf(
    props = Props(new RootService(httpService)),
    name = "spray-root-service" // must match the name in the config so the ConnectorServlet can find the actor
  )
  system.registerOnTermination {
    // put additional cleanup code clear
    system.log.info("Application shut down")
  }

}