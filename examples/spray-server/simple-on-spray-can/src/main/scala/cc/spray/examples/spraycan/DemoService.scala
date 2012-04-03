package cc.spray
package examples.spraycan

import java.util.concurrent.TimeUnit
import akka.actor._
import http._
import StatusCodes._
import MediaTypes._
import typeconversion.ChunkSender
import can.{GetStats, Stats}
import util.{ActorHelpers, Logging}
import akka.util.Duration
import akka.util.duration._
import ActorHelpers._
import java.io.File
import org.parboiled.common.FileUtils
import encoding.{Deflate, Gzip}

trait DemoService extends Directives with Logging {

  val helloService = {
    get {
      path("") {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          completeWith(index)
        }
      } ~
      path("ping") {
        completeWith("PONG!")
      } ~
      path("stream") {
        sendStreamingResponse
      } ~
      path("stream-large-file") {
        encodeResponse(Gzip) {
          getFromFile(largeTempFile)
        }
      } ~
      path("stats") {
        showServerStats
      } ~
      path("timeout") { ctx =>
        // we simply let the request drop to provoke a timeout
      } ~
      path("cached") {
        cache { ctx =>
          in(800.millis) {
            ctx.complete("This resource is only slow the first time!\n" +
              "It was produced on " + DateTime.now.toIsoDateTimeString)
          }
        }
      }
    } ~
    (post | parameter('method ! "post")) {
      path("crash-root-service") { ctx =>
        ctx.complete("Killing the spray root actor in 1 second...")
        in(1000.millis) {
          actor(SprayServerSettings.RootActorId) ! Kill
        }
      } ~
      path("crash-spray-can-server") { ctx =>
        ctx.complete("Killing the spray-can-server actor in 1 second...")
        in(1000.millis) {
          actor("spray-can-server") ! Kill
        }
      } ~
      path("stop") { ctx =>
        ctx.complete("Shutting down in 1 second...")
        in(1000.millis) {
          Actor.registry.foreach(_ ! PoisonPill)
        }
      }
    }
  }

  lazy val index =
    <html>
      <body>
        <h1>Say hello to <i>spray</i> on <i>spray-can</i>!</h1>
        <p>Defined resources:</p>
        <ul>
          <li><a href="/ping">/ping</a></li>
          <li><a href="/stream">/stream</a> (push-mode)</li>
          <li><a href="/stream-large-file">/stream-large-file</a></li>
          <li><a href="/stats">/stats</a></li>
          <li><a href="/timeout">/timeout</a></li>
          <li><a href="/cached">/cached</a></li>
          <li><a href="/crash-root-service?method=post">/crash-root-service</a></li>
          <li><a href="/crash-spray-can-server?method=post">/crash-spray-can-server</a></li>
          <li><a href="/stop?method=post">/stop</a></li>
        </ul>
      </body>
    </html>

  def sendStreamingResponse(ctx: RequestContext) {
    def sendNext(remaining: Int)(chunkSender: ChunkSender) {
      in(500.millis) {
        chunkSender
        .sendChunk(MessageChunk("<li>" + DateTime.now.toIsoDateTimeString + "</li>"))
        .onComplete {
          // we use the successful sending of a chunk as trigger for scheduling the next chunk
          _.value.get match {
            case Right(_) if remaining > 0 => sendNext(remaining - 1)(chunkSender)
            case Right(_) =>
              chunkSender.sendChunk(MessageChunk("</ul><p>Finished.</p></body></html>"))
              chunkSender.close()
            case Left(e) => log.warn("Stopping response streaming due to " + e)
          }
        }
      }
    }
    // we prepend 2048 "empty" bytes to push the browser to immediately start displaying the incoming chunks
    val htmlStart = " " * 2048 + "<html><body><h2>A streaming response</h2><p>(for 15 seconds)<ul>"
    ctx.startChunkedResponse(OK, HttpContent(`text/html`, htmlStart)) foreach sendNext(15)
  }

  def showServerStats(ctx: RequestContext) {
    (sprayCanServerActor ? GetStats).mapTo[Stats].onComplete {
      _.value.get match {
        case Right(stats) => ctx.complete {
          "Uptime              : " + (stats.uptime / 1000.0) + " sec\n" +
          "Requests dispatched : " + stats.requestsDispatched + '\n' +
          "Requests timed out  : " + stats.requestsTimedOut + '\n' +
          "Requests open       : " + stats.requestsOpen + '\n' +
          "Open connections    : " + stats.connectionsOpen + '\n'
        }
        case Left(ex) => ctx.complete(500, "Couldn't get server stats due to " + ex)
      }
    }
  }

  def in[U](duration: Duration)(body: => U) {
    Scheduler.scheduleOnce(() => body, duration.toMillis, TimeUnit.MILLISECONDS)
  }

  lazy val largeTempFile = {
    val file = File.createTempFile("streamingTest", ".txt")
    FileUtils.writeAllText((1 to 1000).map("This is line " + _).mkString("\n"), file)
    file.deleteOnExit()
    file
  }

  lazy val sprayCanServerActor = Actor.registry.actorsFor("spray-can-server").head

}