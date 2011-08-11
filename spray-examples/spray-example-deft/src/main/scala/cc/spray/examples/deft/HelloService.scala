package cc.spray
package examples.deft

import utils.ActorHelpers
import akka.actor.Scheduler
import http.MediaTypes._
import java.util.concurrent.TimeUnit

trait HelloService extends Directives {

  val helloService = {
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          _.complete {
            <html>
              <p>Say hello to <i>spray</i> on <b>Deft</b>!</p>
              <p><a href="/shutdown?method=post">Shutdown</a> this server</p>
            </html>
          }
        }
      }
    } ~
    path("shutdown") {
      (post | parameter('method ! "post")) { ctx =>
        Scheduler.scheduleOnce(shutdown _, 1000, TimeUnit.MILLISECONDS)
        ctx.complete("Will shutdown server in 1 second...")
      }
    }
  }

  def shutdown() {
    ActorHelpers.actor("deft-shutdown-actor") ! 'shutdown
  }
  
}