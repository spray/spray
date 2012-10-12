package docs

import org.specs2.mutable.Specification
import akka.actor.{Props, ActorSystem}                // example-1
import akka.dispatch.Future                           // example-1
import akka.util.Duration
import akka.pattern.ask
import spray.can.client.{HttpDialog, HttpClient}   // example-1
import spray.can.server.HttpServer
import spray.io._                                  // example-1
import spray.util._
import spray.http._                                // example-1
import HttpMethods._                                  // example-1


class HttpDialogExamplesSpec extends Specification {
  //# example-1

  implicit val system = ActorSystem()
  val ioBridge = new IOBridge(system).start()
  val client = system.actorOf(Props(new HttpClient(ioBridge)))

  //#

  val targetHostName = "localhost"
  val port = 8899

  step {
    val handler = system.actorOf(Props(behavior = ctx => {
      case x: HttpRequest => ctx.sender ! HttpResponse(entity = x.uri)
    }))
    val server = system.actorOf(Props(new HttpServer(ioBridge, SingletonHandler(handler))))
    server.ask(HttpServer.Bind("localhost", port))(Duration("1 s")).await
  }

  "example-1" in {
    val response: Future[HttpResponse] =
      HttpDialog(client, targetHostName, port)
      .send(HttpRequest(method = GET, uri = "/"))
      .end
    response.map(_.entity.asString).await === "/" // hide
  }

  "example-2" in {
    import spray.httpx.RequestBuilding._

    val responses: Future[Seq[HttpResponse]] =
      HttpDialog(client, targetHostName, port)
      .send(Post("/shout", "yeah!"))
      .awaitResponse
      .send(Put("/count", "42"))
      .end
    responses.map(_.map(_.entity.asString)).await === "/shout" :: "/count" :: Nil  // hide
  }

  "example-3" in {
    import spray.httpx.RequestBuilding._ // hide

    val responses: Future[Seq[HttpResponse]] =
      HttpDialog(client, targetHostName, port)
      .send(Get("/img/a.gif"))
      .send(Get("/img/b.gif"))
      .send(Get("/img/c.gif"))
      .end
    responses.map(_.map(_.entity.asString)).await === "/img/a.gif" :: "/img/b.gif" :: "/img/c.gif" :: Nil  // hide
  }

  "example-4" in {
    import spray.httpx.RequestBuilding._ // hide

    val response: Future[HttpResponse] =
      HttpDialog(client, targetHostName, port)
      .send(Get("/ping"))
      .reply(response => Get("/ping2", response.entity))
      .end
    response.map(_.entity.asString).await === "/ping2" // hide
  }

  step {
    system.shutdown()
    ioBridge.stop()
  }

}
