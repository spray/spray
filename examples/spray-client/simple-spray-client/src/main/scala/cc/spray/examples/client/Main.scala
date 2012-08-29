package cc.spray.examples.client

import akka.actor.{Props, ActorSystem}
import cc.spray.io.IoWorker
import cc.spray.can.client.HttpClient
import cc.spray.client.HttpConduit
import cc.spray.httpx.SprayJsonSupport
import cc.spray.http._


object Main extends App {
  // we need an ActorSystem to host our application in
  val system = ActorSystem("simple-spray-client")
  def log = system.log

  // every spray-can HttpClient (and HttpServer) needs an IoWorker for low-level network IO
  // (but several servers and/or clients can share one)
  val ioWorker = new IoWorker(system).start()

  // since the ioWorker is not an actor it needs to be stopped separately,
  // here we hook the shutdown of our IoWorker into the shutdown of the applications ActorSystem
  system.registerOnTermination(ioWorker.stop())

  // create and start a spray-can HttpClient
  val httpClient = system.actorOf(
    props = Props(new HttpClient(ioWorker)),
    name = "http-client"
  )

  startExample1()

  // finally we drop the main thread but hook the shutdown of
  // our IoWorker into the shutdown of the applications ActorSystem
  system.registerOnTermination {
    ioWorker.stop()
  }

  ///////////////////////////////////////////////////

  def startExample1() {
    log.info("Getting http://github.com ...")
    // an HttpConduit gives us access to an HTTP server,
    // it manages a pool of connections to _one_ host/port combination
    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, "github.com")),
      name = "http-conduit"
    )

    // send a simple request
    val pipeline = HttpConduit.sendReceive(conduit)
    val responseFuture = pipeline(HttpRequest(method = HttpMethods.GET, uri = "/"))
    responseFuture onComplete {
      case Right(response) =>
        log.info(
          """|Response for GET request to github.com:
             |status : {}
             |headers: {}
             |body   : {}""".stripMargin,
          response.status.value, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString
        )
        system.stop(conduit) // the conduit can be stopped when all operations on it have been completed
        startExample2()

      case Left(error) =>
        log.error(error, "Couldn't get http://github.com")
        system.shutdown()
    }
  }

  def startExample2() {
    log.info("Requesting the elevation of Mt. Everest from Googles Elevation API...")
    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, "maps.googleapis.com")),
      name = "http-conduit"
    )

    import HttpConduit._
    import ElevationJsonProtocol._
    import SprayJsonSupport._
    val pipeline = sendReceive(conduit) ~> unmarshal[GoogleApiResult[Elevation]]

    val responseF = pipeline(Get("/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    responseF onComplete {
      case Right(response) =>
        log.info("The elevation of Mt. Everest is: {} m", response.results.head.elevation)
        system.shutdown()

      case Left(error) =>
        log.error(error, "Couldn't get elevation")
        system.shutdown()
    }
  }
}