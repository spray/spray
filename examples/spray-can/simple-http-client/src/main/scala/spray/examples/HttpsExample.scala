package spray.examples

import com.typesafe.config.ConfigFactory
import scala.util.{Failure, Success}
import akka.actor.{Props, ActorSystem}
import spray.can.client.{HttpDialog, HttpClient}
import spray.io.IOExtension
import spray.http.HttpRequest
import spray.util._


object HttpsExample extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("https-example")
  import system.log

  // every spray-can HttpClient (and HttpServer) needs an IOBridge for low-level network IO
  // (but several servers and/or clients can share one)
  val ioBridge = IOExtension(system).ioBridge

  // create and start a spray-can HttpClient with SSL/TLS support enabled
  val httpClient = system.actorOf(
    props = Props(new HttpClient(ioBridge, ConfigFactory.parseString("spray.can.client.ssl-encryption = on"))),
    name = "https-client"
  )

  // create a very basic HttpDialog that results in a Future[HttpResponse]
  log.info("Dispatching GET request to github.com")
  val responseFuture =
    HttpDialog(httpClient, "github.com", port = 443, HttpClient.SslEnabled)
      .send(HttpRequest(uri = "/spray/spray"))
      .end

  // "hook in" our continuation
  responseFuture onComplete {
    case Success(response) =>
      log.info(
        """|Result from host:
           |status : {}
           |headers: {}
           |body   : {} bytes""".stripMargin,
        response.status,
        response.headers.mkString("\n  ", "\n  ", ""),
        response.entity.buffer.length
      )
      system.shutdown()

    case Failure(error) =>
      log.error("Could not get response due to {}", error)
      system.shutdown()
  }

}
