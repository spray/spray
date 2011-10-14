package cc.spray
package examples.client

import http._
import HttpMethods._
import can.HttpClient
import akka.config.Supervision._
import akka.actor.{PoisonPill, Actor, Supervisor}
import client.{Get, HttpConduit}
import typeconversion.{SprayJsonSupport, DefaultMarshallers}
import utils.Logging

object Main extends App with Logging {

  // start and supervise the spray-can HttpClient actor
  Supervisor(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      List(Supervise(Actor.actorOf(new HttpClient()), Permanent))
    )
  )

  fetchAndShowGithubDotCom()

  fetchAndShowHeightOfMtEverest()

  Actor.registry.actors.foreach(_ ! PoisonPill)

  ///////////////////////////////////////////////////

  def fetchAndShowGithubDotCom() {
    // the HttpConduit gives us access to an HTTP server, it manages a pool of connections
    val conduit = new HttpConduit("github.com")

    // send a simple request
    val responseFuture = conduit.sendReceive(HttpRequest(method = GET, uri = "/"))
    val response = responseFuture.get
    log.info(
      """|Response for GET request to github.com:
         |status : %s
         |headers: %s
         |body   : %s""".stripMargin,
      response.status.value, response.headers, response.content
    )
    conduit.close()
  }

  def fetchAndShowHeightOfMtEverest() {
    log.info("Requesting the elevation of Mt. Everest from Googles Elevation API...")
    val conduit = new HttpConduit("maps.googleapis.com")
      with DefaultMarshallers
      with SprayJsonSupport
      with ElevationJsonProtocol {
      val elevationPipeline = simpleRequest ~> sendReceive ~> unmarshal[GoogleApiResult[Elevation]]
    }
    val responseFuture = conduit.elevationPipeline(Get("/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    log.info("The elevation of Mt. Everest is: %s m", responseFuture.get.results.head.elevation)
    conduit.close()
  }
}