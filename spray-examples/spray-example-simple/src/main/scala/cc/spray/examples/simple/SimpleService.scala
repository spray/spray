package cc.spray
package examples.simple

import utils.ActorHelpers._
import http.MediaTypes._
import java.util.concurrent.TimeUnit
import akka.actor.{Scheduler, Actor, Kill}

trait SimpleService extends Directives {

  val simpleService = {
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          _.complete {
            <html>
              <body>
                <h1>Say hello to <i>spray</i>!</h1>
                <p>Defined resources:</p>
                <ul>
                  <li><a href="/ping">/ping</a></li>
                  <li><a href="/crashRootService">/crashRootService</a></li>
                  <li><a href="/crashHttpService">/crashHttpService</a></li>
                  <li><a href="/timeout">/timeout</a></li>
                  <li><a href="/stop">/stop</a></li>
                </ul>
              </body>
            </html>
          }
        }
      }
    } ~
    path("ping") {
      content(as[Option[String]]) { body =>
        _.complete("PONG! " + body.getOrElse(""))
      }
    } ~
    path("crashRootService") {
      get { ctx =>
        ctx.complete("About to kill the RootService...")
        actor("spray-root-service") ! Kill
      }
    } ~
    path("crashHttpService") {
      get { ctx =>
        ctx.complete("About to kill the RootService...")
        Actor.registry.actorsFor[HttpService].head ! Kill
      }
    } ~
    path("timeout") {
      get { ctx =>
        Scheduler.scheduleOnce(() => ctx.complete("Too late!"), 1500, TimeUnit.MILLISECONDS)
      }
    } ~
    path("stop") {
      get { ctx =>
        ctx.complete("Stopping all actors...")
        Scheduler.scheduleOnce(() => Actor.registry.shutdownAll(), 100, TimeUnit.MILLISECONDS)
      }
    }
  }

  val secondService = {
    path("2nd") {
      get {
        _.complete("A reply from a second service!")
      }
    }
  }

}