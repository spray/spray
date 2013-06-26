package docs

import org.specs2.mutable.Specification
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.util.Timeout
import spray.testkit.Specs2Utils._

class ClientPipeliningExamplesSpec extends Specification {
  implicit val timeout: Timeout = null

  "simple-request-level-pipeline" in compileOnly {
    import spray.http._
    import spray.client.pipelining._

    implicit val system = ActorSystem()
    import system.dispatcher // execution context for futures

    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

    val response: Future[HttpResponse] = pipeline(Get("http://spray.io/"))
  }

  "simple-host-level-pipeline" in compileOnly {
    import akka.io.IO
    import akka.pattern.ask
    import spray.can.Http
    import spray.http._
    import spray.client.pipelining._

    implicit val system = ActorSystem()
    import system.dispatcher // execution context for futures

    val pipeline: Future[SendReceive] =
      for (
        Http.HostConnectorInfo(connector, _) <-
          IO(Http) ? Http.HostConnectorSetup("www.spray.io", port = 80)
      ) yield sendReceive(connector)

    val request = Get("/")
    val response: Future[HttpResponse] = pipeline.flatMap(_(request))
  }

  "large-request-level-pipeline" in compileOnly {
    import spray.http._
    import spray.json.DefaultJsonProtocol
    import spray.httpx.encoding.{Gzip, Deflate}
    import spray.httpx.SprayJsonSupport._
    import spray.client.pipelining._

    case class Order(id: Int)
    case class OrderConfirmation(id: Int)

    object MyJsonProtocol extends DefaultJsonProtocol {
      implicit val orderFormat = jsonFormat1(Order)
      implicit val orderConfirmationFormat = jsonFormat1(OrderConfirmation)
    }
    import MyJsonProtocol._

    implicit val system = ActorSystem()
    import system.dispatcher // execution context for futures

    val pipeline: HttpRequest => Future[OrderConfirmation] = (
      addHeader("X-My-Special-Header", "fancy-value")
      ~> addCredentials(BasicHttpCredentials("bob", "secret"))
      ~> encode(Gzip)
      ~> sendReceive
      ~> decode(Deflate)
      ~> unmarshal[OrderConfirmation]
    )
    val response: Future[OrderConfirmation] =
      pipeline(Post("http://example.com/orders", Order(42)))
  }
}
