package spray.examples

import scala.util.{Success, Failure}
import scala.concurrent.duration._
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import spray.can.client.HttpClientConnection
import spray.http.{HttpResponse, HttpRequest}
import spray.util._


object SimpleExample extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("simple-example")
  import system.log

  // the lowest-level client-side HTTP construct in spray is the
  // HttpClientConnection actor, which manages one single HTTP connection
  // over its lifetime.
  val connection = system.actorOf(Props(new HttpClientConnection))

  // we open the connection by telling it to `Connect`, after having
  // received the `Connected` reply we can send requests and expect
  // responses as a reply, finally we close with a `Close` command
  import HttpClientConnection._   // import the protocol messages
  implicit val timeout: Timeout = 5 seconds span
  val responseFuture = for {
      Connected(_)                       <- connection ? Connect("github.com")
      response@ HttpResponse(_, _, _, _) <- connection ? HttpRequest(uri = "/")
      // Closed(_, _) <- connection ? Close(ConnectionCloseReasons.CleanClose) // not required if the server closes
    } yield response

  // "hook in" our continuation
  responseFuture onComplete {
    case Success(response) =>
      log.info(
        """|Result from host:
           |status : {}
           |headers: {}
           |body   : {}""".stripMargin,
        response.status,
        response.headers.mkString("\n  ", "\n  ", ""),
        response.entity.asString
      )
      system.shutdown()

    case Failure(error) =>
      log.error("Could not get response due to {}", error)
      system.shutdown()
  }
}
