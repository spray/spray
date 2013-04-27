package docs

import akka.actor.{ActorSystem, Actor, Props}
import spray.can.Http
import akka.io.IO

//# source-quote

object Main extends App {
  implicit val system = ActorSystem("my-system")

  val handler = system.actorOf(Props[MyService])

  IO(Http) ! Http.Bind(handler, interface = "localhost", port = 8080)
}
//#

class MyService extends Actor {
  def receive: Receive = ???
}