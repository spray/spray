package cc.spray.examples.client

import akka.actor.{Props, ActorSystem}
import cc.spray.io.IoWorker
import cc.spray.can.client.HttpClient
import cc.spray.typeconversion.SprayJsonSupport
import cc.spray.client.{Get, HttpConduit}
import cc.spray.http.{HttpMethods, HttpRequest}
import cc.spray.util._


object Main extends App {

  implicit val system = ActorSystem()
  def log = system.log

  // every spray-can HttpClient (and HttpServer) needs an IoWorker for low-level network IO
  // (but several servers and/or clients can share one)
  val ioWorker = new IoWorker(system).start()

  // create and start a spray-can HttpClient
  val httpClient = system.actorOf(
    props = Props(new HttpClient(ioWorker)),
    name = "http-client"
  )

  fetchAndShowGithubDotCom()

  fetchAndShowHeightOfMtEverest()

  system.shutdown()
  ioWorker.stop()

  ///////////////////////////////////////////////////

  def fetchAndShowGithubDotCom() {
    // an HttpConduit gives us access to an HTTP server, it manages a pool of connections
    val conduit = new HttpConduit(httpClient, "github.com")

    // send a simple request
    val responseFuture = conduit.sendReceive(HttpRequest(method = HttpMethods.GET, uri = "/"))
    val response = responseFuture.await
    log.info(
      """|Response for GET request to github.com:
         |status : {}
         |headers: {}
         |body   : {}""".stripMargin,
      response.status.value, response.headers.mkString("\n  ", "\n  ", ""), response.content
    )
    conduit.close() // the conduit should be closed when all operations on it have been completed
  }

  def fetchAndShowHeightOfMtEverest() {
    log.info("Requesting the elevation of Mt. Everest from Googles Elevation API...")
    val conduit = new HttpConduit(httpClient, "maps.googleapis.com")
      with SprayJsonSupport
      with ElevationJsonProtocol {
      val elevationPipeline = simpleRequest ~> sendReceive ~> unmarshal[GoogleApiResult[Elevation]]
    }
    val responseF = conduit.elevationPipeline(Get("/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    log.info("The elevation of Mt. Everest is: {} m", responseF.await.results.head.elevation)
    conduit.close()
  }
}