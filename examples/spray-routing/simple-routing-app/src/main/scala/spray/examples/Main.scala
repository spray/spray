package spray.examples

import spray.routing.SimpleRoutingApp
import spray.http.MediaTypes._
import akka.dispatch.Promise
import akka.util.Duration
import spray.util._


object Main extends App with SimpleRoutingApp {

// currently doesn't compile under Scala 2.9.2 due to a compiler bug:
// java.lang.Error: symbol value $buf$1 does not exist in spray.examples.Main$delayedInit$body.apply
// we'll isolate and report later

//  startServer(interface = "localhost", port = 8080) {
//    get {
//      path("") {
//        redirect("/hello")
//      } ~
//        path("hello") {
//          respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
//            complete {
//              <html>
//                <h1>Say hello to <em>spray</em> on <em>spray-can</em>!</h1>
//                <p>(<a href="/stop?method=post">stop server</a>)</p>
//              </html>
//            }
//          }
//        }
//    } ~
//      (post | parameter('method ! "post")) {
//        path("stop") {
//          complete {
//            system.scheduler.scheduleOnce(Duration(1, "sec")) {
//              system.shutdown()
//            }
//            "Shutting down in 1 second..."
//          }
//        }
//      }
//  }

}