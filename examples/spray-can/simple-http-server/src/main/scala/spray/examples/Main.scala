package spray.examples

import spray.can.server.SprayCanHttpServerApp
import akka.actor.Props


object Main extends App with SprayCanHttpServerApp with MySslConfiguration {

  // the handler actor replies to incoming HttpRequests
  val handler = system.actorOf(Props[DemoService])

  // create a new HttpServer using our handler tell it where to bind to
  newHttpServer(handler) ! Bind(interface = "localhost", port = 8080)

}