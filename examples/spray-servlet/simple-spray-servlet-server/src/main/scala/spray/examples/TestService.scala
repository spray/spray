package spray.examples

import akka.util.duration._
import akka.actor._
import spray.servlet.ServletError
import spray.http._
import MediaTypes._
import HttpMethods._


class TestService extends Actor with ActorLogging {

  def receive = {

    case HttpRequest(GET, "/", _, _, _) =>
      sender ! index

    case HttpRequest(GET, "/ping", _, _, _) =>
      sender ! HttpResponse(entity = "PONG!")

    case HttpRequest(GET, "/stream", _, _, _) =>
      val peer = sender // since the Props creator is executed asyncly we need to save the sender ref
      context.actorOf(Props(new Streamer(peer, 20)))

    case HttpRequest(GET, "/crash", _, _, _) =>
      sender ! HttpResponse(entity = "About to throw an exception in the request handling actor, " +
        "which triggers an actor restart")
      throw new RuntimeException("BOOM!")

    case HttpRequest(GET, "/timeout", _, _, _) =>
      log.info("Dropping request, triggering a timeout")

    case HttpRequest(GET, "/timeout/timeout", _, _, _) =>
      log.info("Dropping request, triggering a timeout")

    case _: HttpRequest => sender ! HttpResponse(404, "Unknown resource!")

    case Timeout(HttpRequest(_, "/timeout/timeout", _, _, _)) =>
      log.info("Dropping Timeout message")

    case Timeout(request: HttpRequest) =>
      sender ! HttpResponse(500, "The " + request.method + " request to '" + request.uri + "' has timed out...")

    case ServletError(error) =>
      context.children.foreach(_ ! CancelStream(sender, error))
  }

  ////////////// helpers //////////////

  lazy val index = HttpResponse(
    entity = HttpBody(`text/html`,
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

  case class CancelStream(peer: ActorRef, error: Throwable)

  class Streamer(peer: ActorRef, var count: Int) extends Actor with ActorLogging {
    log.debug("Starting streaming response ...")
    peer ! ChunkedResponseStart(HttpResponse(entity = " " * 2048))
    val chunkGenerator = context.system.scheduler.schedule(100.millis, 100.millis, self, 'Tick)

    protected def receive = {
      case 'Tick if count > 0 =>
        log.info("Sending response chunk ...")
        peer ! MessageChunk(DateTime.now.toIsoDateTimeString + ", ")
        count -= 1
      case 'Tick =>
        log.info("Finalizing response stream ...")
        chunkGenerator.cancel()
        peer ! MessageChunk("\nStopped...")
        peer ! ChunkedMessageEnd()
        context.stop(self)
      case CancelStream(ref, error) => if (ref == peer) {
        log.info("Canceling response stream due to {} ...", error)
        chunkGenerator.cancel()
        context.stop(self)
      }
    }
  }

}