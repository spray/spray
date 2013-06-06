package spray.examples

import akka.util.duration._
import akka.io.Tcp
import akka.actor._
import spray.util._
import spray.http._
import MediaTypes._
import HttpMethods._


class DemoService extends Actor with SprayActorLogging {

  def receive = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      sender ! index

    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
      sender ! HttpResponse(entity = "PONG!")

    case HttpRequest(GET, Uri.Path("/stream"), _, _, _) =>
      val client = sender // since the Props creator is executed asyncly we need to save the sender ref
      context.actorOf(Props(new Streamer(client, 20)))

    case HttpRequest(GET, Uri.Path("/crash"), _, _, _) =>
      sender ! HttpResponse(entity = "About to throw an exception in the request handling actor, " +
        "which triggers an actor restart")
      sys.error("BOOM!")

    case HttpRequest(GET, Uri.Path("/timeout"), _, _, _) =>
      log.info("Dropping request, triggering a timeout")

    case HttpRequest(GET, Uri.Path("/changetimeout"), _, _, _) =>
      sender ! SetRequestTimeout(60.seconds)
      log.info("Changing timeout initially set by 'spray.servlet.request-timeout' property and triggering timeout")

    case HttpRequest(GET, Uri.Path("/timeout/timeout"), _, _, _) =>
      log.info("Dropping request, triggering a timeout")

    case _: HttpRequest => sender ! HttpResponse(404, "Unknown resource!")

    case Timedout(HttpRequest(_, Uri.Path("/timeout/timeout"), _, _, _)) =>
      log.info("Dropping Timeout message")

    case Timedout(request: HttpRequest) =>
      sender ! HttpResponse(500, "The " + request.method + " request to '" + request.uri + "' has timed out...")
  }

  ////////////// helpers //////////////

  lazy val index = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Say hello to <i>spray-servlet</i>!</h1>
          <p>Defined resources:</p>
          <ul>
            <li><a href="/ping">/ping</a></li>
            <li><a href="/stream">/stream</a></li>
            <li><a href="/crash">/crash</a></li>
            <li><a href="/timeout">/timeout</a></li>
            <li><a href="/timeout/timeout">/timeout/timeout</a></li>
          </ul>
        </body>
      </html>.toString
    )
  )

  // simple case class whose instances we use as send confirmation message for streaming chunks
  case class Ok(remaining: Int)

  class Streamer(client: ActorRef, count: Int) extends Actor with SprayActorLogging {
    log.debug("Starting streaming response ...")

    // we use the successful sending of a chunk as trigger for scheduling the next chunk
    client ! ChunkedResponseStart(HttpResponse(entity = " " * 2048)).withAck(Ok(count))

    def receive = {
      case Ok(0) =>
        log.info("Finalizing response stream ...")
        client ! MessageChunk("\nStopped...")
        client ! ChunkedMessageEnd()
        context.stop(self)

      case Ok(remaining) =>
        log.info("Sending response chunk ...")
        import context.dispatcher
        context.system.scheduler.scheduleOnce(100.millis) {
          client ! MessageChunk(DateTime.now.toIsoDateTimeString + ", ").withAck(Ok(remaining - 1))
        }

      case ev: Tcp.ConnectionClosed =>
        log.info("Canceling response stream due to {} ...", ev)
        context.stop(self)
    }
  }

}
