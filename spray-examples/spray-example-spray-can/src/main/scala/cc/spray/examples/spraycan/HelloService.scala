package cc.spray
package examples.spraycan

import http.MediaTypes._
import java.util.concurrent.TimeUnit
import akka.actor.{PoisonPill, Actor, Scheduler}

trait HelloService extends Directives {

  val helloService = {
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          _.complete {
            <html>
              <p>Say hello to <i>spray</i> on <b>spray-can</b>!</p>
              <p><a href="/shutdown?method=post">Shutdown</a> this server</p>
            </html>
          }
        }
      }
    } ~
    path("shutdown") {
      (post | parameter('method ! "post")) { ctx =>
        Scheduler.scheduleOnce(() => Actor.registry.foreach(_ ! PoisonPill), 1000, TimeUnit.MILLISECONDS)
        ctx.complete("Will shutdown server in 1 second...")
      }
    }
  }

}