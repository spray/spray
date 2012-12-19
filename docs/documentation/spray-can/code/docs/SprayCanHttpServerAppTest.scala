package docs

import akka.actor.{Actor, Props}

//# source-quote
import spray.can.server.SprayCanHttpServerApp

object Main extends App with SprayCanHttpServerApp {
  val handler = system.actorOf(Props[MyService])
  newHttpServer(handler) ! Bind(interface = "localhost", port = 8080)
}
//#

class MyService extends Actor {
  def receive: Receive = ???
}