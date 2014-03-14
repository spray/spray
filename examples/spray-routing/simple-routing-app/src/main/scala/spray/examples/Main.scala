package spray.examples

import scala.concurrent.duration._
import akka.actor.ActorSystem
import spray.routing.SimpleRoutingApp
import spray.http._
import MediaTypes._
import scala.util.{Failure, Success}

object Main extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("simple-routing-app")
  import system.dispatcher

  startServer("localhost", port = 8080) {
    get {
      pathSingleSlash {
        redirect("/hello", StatusCodes.Found)
      } ~
      path("hello") {
        complete {
          <html>
            <h1>Say hello to <em>spray</em> on <em>spray-can</em>!</h1>
            <p>(<a href="/stop?method=post">stop server</a>)</p>
          </html>
        }
      }
    } ~
    (post | parameter('method ! "post")) {
      path("stop") {
        complete {
          system.scheduler.scheduleOnce(1.second)(system.shutdown())(system.dispatcher)
          "Shutting down in 1 second..."
        }
      }
    }
  }.onComplete {
    case Success(b) =>
      println(s"Successfully bound to ${b.localAddress}")
    case Failure(ex) =>
      println(ex.getMessage)
      system.shutdown()
  }
}