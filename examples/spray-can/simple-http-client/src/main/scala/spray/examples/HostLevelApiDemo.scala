package spray.examples

import akka.dispatch.Future
import akka.util.duration._
import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.ask
import akka.io.IO
import spray.can.{HostConnectorInfo, HostConnectorSetup, Http}
import spray.http._
import HttpMethods._

trait HostLevelApiDemo {
  private implicit val timeout: Timeout = 5.seconds

  // With the host-level API you ask the spray-can HTTP infrastructure to setup an
  // "HttpHostConnector" for you, which is an entity that manages a pool of connection to
  // one particular host. Once set up you can send the host connector HttpRequest instances,
  // which it will schedule across a connection from its pool (according to its configuration)
  // and deliver the responses back to the request sender

  def demoHostLevelApi(host: String)(implicit system: ActorSystem): Future[ProductVersion] = {
    import system.dispatcher // execution context for future transformations below
    for {
      HostConnectorInfo(hostConnector, _) <- IO(Http) ? HostConnectorSetup(host, port = 80)
      response <- hostConnector.ask(HttpRequest(GET, "/")).mapTo[HttpResponse]
      _ <- hostConnector ? Http.CloseAll
    } yield {
      system.log.info("Host-Level API: received {} response with {} bytes",
        response.status, response.entity.buffer.length)
      response.header[HttpHeaders.Server].get.products.head
    }
  }

}
