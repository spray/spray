package spray.examples

import scala.concurrent.duration.Duration
import spray.routing.SimpleRoutingApp
import spray.http.MediaTypes._


object Main extends App with SimpleRoutingApp {

  startServer(interface = "localhost", port = 8080) {
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
          system.scheduler.scheduleOnce(Duration(1, "sec")) {
            system.shutdown()
          }
          "Shutting down in 1 second..."
        }
      }
    }
  }

}