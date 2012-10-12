package spray.examples

import akka.actor.{Props, ActorSystem}
import spray.io.IOBridge
import spray.can.client.{HttpDialog, HttpClient}
import spray.http.HttpRequest


object SimpleExample extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("simple-example")
  def log = system.log

  // every spray-can HttpClient (and HttpServer) needs an IOBridge for low-level network IO
  // (but several servers and/or clients can share one)
  val ioBridge = new IOBridge(system).start()

  // create and start the spray-can HttpClient
  val httpClient = system.actorOf(
    props = Props(new HttpClient(ioBridge)),
    name = "http-client"
  )

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

  // finally we drop the main thread but hook the shutdown of
  // our IOBridge into the shutdown of the applications ActorSystem
  system.registerOnTermination {
    ioBridge.stop()
  }
}
