package cc.spray.servlet.example

import akka.actor.{Props, ActorSystem}


class Boot {
  // we need an ActorSystem to host our application in
  val system = ActorSystem("example")

  // the service actor replies to incoming HttpRequests
  val serviceActor = system.actorOf(Props[TestService])

  system.registerOnTermination {
    // put additional cleanup code here
    system.log.info("Application shut down")
  }
}