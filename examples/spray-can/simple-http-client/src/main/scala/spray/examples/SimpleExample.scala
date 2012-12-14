package spray.examples

import scala.util.{Success, Failure}
import akka.actor.ActorSystem
import spray.can.client.{DefaultHttpClient, HttpDialog}
import spray.http.HttpRequest
import spray.util._


object SimpleExample extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("simple-example")
  import system.log

  // create and start the default spray-can HttpClient
  val httpClient = DefaultHttpClient(system)

  // create a very basic HttpDialog that results in a Future[HttpResponse]
  log.info("Dispatching GET request to github.com")
  val responseFuture =
    HttpDialog(httpClient, "github.com")
      .send(HttpRequest(uri = "/"))
      .end

  // "hook in" our continuation
  responseFuture onComplete {
    case Success(response) =>
      log.info(
        """|Result from host:
           |status : {}
           |headers: {}
           |body   : {}""".stripMargin,
        response.status, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString
      )
      system.shutdown()

    case Failure(error) =>
      log.error("Could not get response due to {}", error)
      system.shutdown()
  }

}
