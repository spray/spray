package spray.examples

import akka.dispatch.Future
import akka.util.duration._
import akka.io.IO
import akka.util.Timeout
import akka.pattern.ask
import akka.actor._
import spray.can.Http
import spray.util.SprayActorLogging
import spray.http._
import HttpMethods._


trait ConnectionLevelApiDemo {
  private implicit val timeout: Timeout = 5.seconds

  def demoConnectionLevelApi(host: String)(implicit system: ActorSystem): Future[ProductVersion] = {
    val actor = system.actorOf(Props(new MyRequestActor(host)), name = "my-request-actor")
    val future = actor ? HttpRequest(GET, "/")
    future.mapTo[ProductVersion]
  }

  // The connection-level API is the lowest-level way to access the spray-can client-side infrastructure.
  // With it you are in charge of establishing, using, and tearing down the HTTP connections yourself.
  // The benefit is that you have complete control over when connections are being established and torn down
  // as well as how requests are scheduled onto them.

  // Actor that manages the lifecycle of a single HTTP connection for a single request
  class MyRequestActor(host: String) extends Actor with SprayActorLogging {
    import context.system

    def receive: Receive = {
      case request: HttpRequest =>
        // start by establishing a new HTTP connection
        IO(Http) ! Http.Connect(host, port = 80)
        context.become(connecting(sender, request))
    }

    def connecting(commander: ActorRef, request: HttpRequest): Receive = {
      case _: Http.Connected =>
        // once connected, we can send the request across the connection
        sender ! request
        context.become(waitingForResponse(commander))

      case Http.CommandFailed(Http.Connect(address, _, _, _, _)) =>
        log.warning("Could not connect to {}", address)
        commander ! Status.Failure(new RuntimeException("Connection error"))
        context.stop(self)
    }

    def waitingForResponse(commander: ActorRef): Receive = {
      case response@ HttpResponse(status, entity, _, _) =>
        log.info("Connection-Level API: received {} response with {} bytes", status, entity.buffer.length)
        sender ! Http.Close
        context.become(waitingForClose(commander, response))

      case ev@(Http.SendFailed(_) | Timedout(_))=>
        log.warning("Received {}", ev)
        commander ! Status.Failure(new RuntimeException("Request error"))
        context.stop(self)
    }

    def waitingForClose(commander: ActorRef, response: HttpResponse): Receive = {
      case ev: Http.ConnectionClosed =>
        log.debug("Connection closed ({})", ev)
        commander ! Status.Success(response.header[HttpHeaders.Server].get.products.head)
        context.stop(self)

      case Http.CommandFailed(Http.Close) =>
        log.warning("Could not close connection")
        commander ! Status.Failure(new RuntimeException("Connection close error"))
        context.stop(self)
    }
  }
}
