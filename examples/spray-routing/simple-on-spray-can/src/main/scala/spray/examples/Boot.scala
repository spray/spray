package spray.examples

import akka.actor.Props
import spray.can.server.SprayCanHttpServerApp


object Boot extends App with SprayCanHttpServerApp {

  // create and start our service actor
  val service = system.actorOf(Props[DemoServiceActor], "demo-service")

  // create a new HttpServer using our handler tell it where to bind to
  newHttpServer(service) ! Bind(interface = "localhost", port = 8080)
}