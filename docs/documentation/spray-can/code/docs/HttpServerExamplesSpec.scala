package docs

import org.specs2.mutable.Specification
import akka.actor.Actor
import spray.http._
import HttpMethods._


class HttpServerExamplesSpec extends Specification {

  class Actor1 extends Actor {
    //# example-1
    def receive = {
      case HttpRequest(GET, "/ping", _, _, _) =>
        sender ! HttpResponse(entity = "PONG")
    }
    //#
  }

  class Actor2 extends Actor {
    //# example-2
    def receive = {
      case HttpRequest(GET, "/ping", _, _, _) =>
        sender ! HttpResponse(entity = "PONG").withSentAck("ok")

      case "ok" => println("Response was sent successfully")
    }
    //#
  }

  "example" in {
    success
  }
}
