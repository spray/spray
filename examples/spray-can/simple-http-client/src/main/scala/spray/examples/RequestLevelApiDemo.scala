package spray.examples

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.http._
import spray.can.Http
import HttpMethods._

trait RequestLevelApiDemo {
  private implicit val timeout: Timeout = 5.seconds

  def demoRequestLevelApi(host: String)(implicit system: ActorSystem): Future[ProductVersion] = {
    import system.dispatcher // execution context for future transformation below
    for {
      response <- IO(Http).ask(HttpRequest(GET, Uri(s"http://$host/"))).mapTo[HttpResponse]
      _ <- IO(Http) ? Http.CloseAll
    } yield {
      system.log.info("Request-Level API: received {} response with {} bytes",
        response.status, response.entity.buffer.length)
      response.header[HttpHeaders.Server].get.products.head
    }
  }

}