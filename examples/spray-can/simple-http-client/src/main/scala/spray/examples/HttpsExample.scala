package spray.examples

import scala.util.{Failure, Success}
import akka.actor.ActorSystem
import spray.can.client.{DefaultHttpClient, HttpDialog, HttpClient}
import spray.http.HttpRequest
import spray.util._


object HttpsExample extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("https-example")
  import system.log

  // create and start the default spray-can HttpClient
  val httpClient = DefaultHttpClient(system)

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
