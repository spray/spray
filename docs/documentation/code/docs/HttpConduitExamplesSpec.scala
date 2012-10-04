package docs

import org.specs2.mutable.Specification

// setup
import akka.util.Duration
import akka.pattern.ask
import akka.actor.{Props, ActorSystem}           // setup
import akka.dispatch.Future                      // setup
import cc.spray.io.pipelining.SingletonHandler
import cc.spray.io.IOBridge                      // setup
import cc.spray.json.DefaultJsonProtocol
import cc.spray.can.client.HttpClient            // setup
import cc.spray.can.server.HttpServer
import cc.spray.client.HttpConduit               // setup
import cc.spray.util._
import cc.spray.http._                           // setup
import HttpMethods._                             // setup


class HttpConduitExamplesSpec extends Specification {
  //# setup

  val system = ActorSystem()
  val ioBridge = new IOBridge(system).start()
  val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))
  val targetHostName = "localhost" // hide
  val port = 8898                  // hide

  val conduit = system.actorOf(
    props = Props(new HttpConduit(httpClient, targetHostName, port)),
    name = "http-conduit"
  )
  //#

  step {
    val handler = system.actorOf(Props(behavior = ctx => {
      case x@HttpRequest(POST, "/orders", _, _, _) =>
        import cc.spray.httpx.encoding.{Gzip, Deflate}
        ctx.sender ! Deflate.encode(HttpResponse(entity = Gzip.decode(x).entity))
      case x: HttpRequest => ctx.sender ! HttpResponse(entity = x.uri)
    }))
    val server = system.actorOf(Props(new HttpServer(ioBridge, SingletonHandler(handler))))
    server.ask(HttpServer.Bind("localhost", port))(Duration("1 s")).await
  }

  "example-1" in {
    val pipeline = HttpConduit.sendReceive(conduit)   // simple-pipeline
    val response: Future[HttpResponse] = pipeline(HttpRequest(method = GET, uri = "/"))  // response-future
    response.map(_.entity.asString).await === "/"
  }

  "example-2" in {
    import cc.spray.httpx.encoding.{Gzip, Deflate}
    import cc.spray.httpx.SprayJsonSupport._

    case class Order(id: Int)
    case class OrderConfirmation(id: Int)

    object MyJsonProtocol extends DefaultJsonProtocol {
      implicit val orderFormat = jsonFormat1(Order)
      implicit val orderConfirmationFormat = jsonFormat1(OrderConfirmation)
    }

    import HttpConduit._
    import MyJsonProtocol._

    val pipeline: HttpRequest => Future[OrderConfirmation] = (
      addHeader("X-My-Special-Header", "fancy-value")
      ~> addCredentials(BasicHttpCredentials("bob", "secret"))
      ~> encode(Gzip)
      ~> sendReceive(conduit)
      ~> decode(Deflate)
      ~> unmarshal[OrderConfirmation]
    )
    val confirmation: Future[OrderConfirmation] =
      pipeline(Post("/orders", Order(42)))
    confirmation.await === OrderConfirmation(42) // hide
  }

  step {
    system.shutdown()
    ioBridge.stop()
  }

}
