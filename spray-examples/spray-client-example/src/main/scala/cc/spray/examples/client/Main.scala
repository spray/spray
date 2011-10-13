package cc.spray
package examples.client

import http._
import HttpMethods._
import client.HttpConduit
import can.HttpClient
import akka.config.Supervision._
import akka.actor.{PoisonPill, Actor, Supervisor}
import org.slf4j.LoggerFactory

object Main extends App {
  val log = LoggerFactory.getLogger(getClass)

  // start and supervise the spray-can HttpClient actor
  Supervisor(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      List(Supervise(Actor.actorOf(new HttpClient()), Permanent))
    )
  )

  // the HttpConduit gives us access to an HTTP server, it manages a pool of connections
  val conduit = new HttpConduit("github.com")

  // send a simple request
  val responseFuture = conduit.send(HttpRequest(method = GET, uri = "/"))

  // attach a "continuation" function to the future
  responseFuture.await.value.get match {
    case Right(response) => show(response)
    case error => log.error("Error: {}", error)
  }
  conduit.close()
  Actor.registry.actors.foreach(_ ! PoisonPill)

  ///////////////////////////////////////////////////

  def show(response: HttpResponse) {
    log.info(
      """|Result from host:
         |status : {}
         |headers: {}
         |body   : {}""".stripMargin,
      Array[AnyRef](response.status.value :java.lang.Integer, response.headers, response.content)
    )
  }
}