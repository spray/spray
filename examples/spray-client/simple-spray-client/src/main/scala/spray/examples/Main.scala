package spray.examples

import scala.util.{Success, Failure}
import scala.concurrent.duration._
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import spray.client.HttpClient
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import spray.http._
import spray.util._


object Main extends App {
  // we need an ActorSystem to host our application in
  implicit val timeout: Timeout = 5 seconds span
  implicit val system = ActorSystem("simple-spray-client")
  import system.log

  // an HttpClient is the highest-level client-side HTTP construct in spray,
  // simply send it an HTTP request with a Host header or an absolute URI
  // and wait for the response
  // an HttpClient can handle many thousand requests and is designed as a
  // long-lived actor, however, you can created several ones if you need to
  val httpClient = system.actorOf(Props(new HttpClient), "http-client")

  startExample1()

  ///////////////////////////////////////////////////

  def startExample1() {
    log.info("Getting http://github.com ...")

    // send a simple request
    val responseFuture = httpClient.ask(Get("http://github.com")).mapTo[HttpResponse]
    responseFuture onComplete {
      case Success(response) =>
        log.info(
          """|Response for GET request to github.com:
             |status : {}
             |headers: {}
             |body   : {}""".stripMargin,
          response.status.value, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString
        )
        startExample2()

      case Failure(error) =>
        log.error(error, "Couldn't get http://github.com")
        system.shutdown()
    }
  }

  def startExample2() {
    log.info("Requesting the elevation of Mt. Everest from Googles Elevation API...")
    import ElevationJsonProtocol._
    import SprayJsonSupport._
    val pipeline = sendReceive(httpClient) ~> unmarshal[GoogleApiResult[Elevation]]

    val responseF = pipeline {
      Get("http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    }
    responseF onComplete {
      case Success(response) =>
        log.info("The elevation of Mt. Everest is: {} m", response.results.head.elevation)
        system.shutdown() // also stops all conduits (since they are actors)

      case Failure(error) =>
        log.error(error, "Couldn't get elevation")
        system.shutdown() // also stops all conduits (since they are actors)
    }
  }
}