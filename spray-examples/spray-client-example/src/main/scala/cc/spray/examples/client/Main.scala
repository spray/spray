package cc.spray
package examples.client

import http._
import akka.config.Supervision._
import org.slf4j.LoggerFactory
import can.HttpClient
import akka.actor.{PoisonPill, Actor, Supervisor}
import client.HttpConduit

object Main extends App {
  val log = LoggerFactory.getLogger(getClass)

  // start and supervise the HttpClient actor
  Supervisor(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      List(Supervise(Actor.actorOf(new HttpClient()), Permanent))
    )
  )

  val conduit = new HttpConduit("github.com")
  conduit.send(HttpRequest()).onComplete { future =>
    future.value match {
      case Some(Right(response)) => show(response)
      case error => log.error("Error: {}", error)
    }
    conduit.close()
    Actor.registry.actors.foreach(_ ! PoisonPill)
  }

  def show(response: HttpResponse) {
    log.info(
      """|Result from host:
         |status : {}
         |headers: {}
         |body   : {}""".stripMargin,
      Array[AnyRef](response.status.value, response.headers, response.content)
    )
  }
}