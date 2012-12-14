package spray.examples

import akka.actor.ActorSystem
import spray.can.client.{DefaultHttpClient, HttpDialog, HttpClient}
import spray.http.HttpRequest


object HttpsExample extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("https-example")
  def log = system.log

  // create and start the default spray-can HttpClient
  val httpClient = DefaultHttpClient(system)

  // create a very basic HttpDialog that results in a Future[HttpResponse]
  log.info("Dispatching GET request to github.com")
  val responseFuture =
    HttpDialog(httpClient, "github.com", port = 443, HttpClient.Encrypted)
      .send(HttpRequest(uri = "/spray/spray"))
      .end

  // "hook in" our continuation
  responseFuture onComplete {
    case Right(response) =>
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

    case Left(error) =>
      log.error("Could not get response due to {}", error)
      system.shutdown()
  }

}
