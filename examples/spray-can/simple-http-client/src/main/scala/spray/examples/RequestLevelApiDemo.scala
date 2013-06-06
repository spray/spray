package spray.examples

import akka.dispatch.Future
import akka.util.duration._
import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.http._
import spray.can.Http
import HttpMethods._

trait RequestLevelApiDemo {
  private implicit val timeout: Timeout = 5.seconds

  // The request-level API is the highest-level way to access the spray-can client-side infrastructure.
  // All you have to do is to send an HttpRequest instance to `IO(Http)` and wait for the response.
  // The spray-can HTTP infrastructure looks at the URI (or the Host header if the URI is not absolute)
  // to figure out which host to send the request to. It then sets up a HostConnector for that host
  // (if it doesn't exist yet) and forwards it the request.
  def demoRequestLevelApi(host: String)(implicit system: ActorSystem): Future[ProductVersion] = {
    import system.dispatcher // execution context for future transformation below
    for {
      response <- IO(Http).ask(HttpRequest(GET, Uri("http://"+host+"/"))).mapTo[HttpResponse]
      _ <- IO(Http) ? Http.CloseAll
    } yield {
      system.log.info("Request-Level API: received {} response with {} bytes",
        response.status, response.entity.buffer.length)
      response.header[HttpHeaders.Server].get.products.head
    }
  }

}
