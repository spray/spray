package spray.examples

import akka.actor.ActorSystem
import spray.can.client.{DefaultHttpClient, HttpDialog}
import spray.http.HttpRequest


object SimpleExample extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("simple-example")
  def log = system.log

  // create and start the default spray-can HttpClient
  val httpClient = DefaultHttpClient(system)

  // create a very basic HttpDialog that results in a Future[HttpResponse]
  log.info("Dispatching GET request to github.com")
  val responseF =
    HttpDialog(httpClient, "github.com")
      .send(HttpRequest(uri = "/"))
      .end

  // "hook in" our continuation
  responseF onComplete {
    case Right(response) =>
      log.info(
        """|Result from host:
           |status : {}
           |headers: {}
           |body   : {}""".stripMargin,
        response.status, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString
      )
      system.shutdown()

    case Left(error) =>
      log.error("Could not get response due to {}", error)
      system.shutdown()
  }

}
