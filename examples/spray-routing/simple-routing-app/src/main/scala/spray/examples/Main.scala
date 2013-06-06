package spray.examples

import akka.util.duration._
import akka.actor.ActorSystem
import spray.routing.SimpleRoutingApp
import spray.http.MediaTypes._

object Main extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("simple-routing-app")

  startServer("localhost", port = 8080) {
    get {
      path("") {
        redirect("/hello")
      } ~
      path("hello") {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            <html>
              <h1>Say hello to <em>spray</em> on <em>spray-can</em>!</h1>
              <p>(<a href="/stop?method=post">stop server</a>)</p>
            </html>
          }
        }
      }
    } ~
    (post | parameter('method ! "post")) {
      path("stop") {
        complete {
          system.scheduler.scheduleOnce(1.second)(system.shutdown())
          "Shutting down in 1 second..."
        }
      }
    }
  }

}
