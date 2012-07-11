package cc.spray.examples.spraycan

import java.io.File
import org.parboiled.common.FileUtils
import akka.util.Duration
import akka.util.duration._
import cc.spray.http._
import cc.spray.typeconversion.ChunkSender
import cc.spray.encoding.Gzip
import cc.spray.can.server.HttpServer
import cc.spray.{RequestContext, Directives}
import StatusCodes._
import MediaTypes._

trait DemoService extends Directives {

  val demoService = {
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
              "It was produced on " + DateTime.now.toIsoDateTimeString + "\n\n" +
              "(Note that your browser will likely enforce a cache invalidation with a\n" +
              "`Cache-Control: max-age=0` header, so you might need to `curl` this\n" +
              "resource in order to be able to see the cache effect!)")
          }
        }
      }
    } ~
    (post | parameter('method ! "post")) {
      path("stop") { ctx =>
        ctx.complete("Shutting down in 1 second...")
        in(1000.millis) {
          actorSystem.shutdown()
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
          case Right(_) if remaining > 0 => sendNext(remaining - 1)(chunkSender)
          case Right(_) =>
            chunkSender.sendChunk(MessageChunk("</ul><p>Finished.</p></body></html>"))
            chunkSender.close()
          case Left(e) => actorSystem.log.warning("Stopping response streaming due to {}", e)
        }
      }
    }
    // we prepend 2048 "empty" bytes to push the browser to immediately start displaying the incoming chunks
    val htmlStart = " " * 2048 + "<html><body><h2>A streaming response</h2><p>(for 15 seconds)<ul>"
    ctx.startChunkedResponse(OK, HttpContent(`text/html`, htmlStart)) foreach sendNext(15)
  }

  def showServerStats(ctx: RequestContext) {
    import akka.pattern.ask
    httpServer.ask(HttpServer.GetStats)(1.second).mapTo[HttpServer.Stats].onComplete {
      case Right(stats) => ctx.complete {
        "Uptime                : " + stats.uptime.printHMS + '\n' +
        "Total requests        : " + stats.totalRequests + '\n' +
        "Open requests         : " + stats.openRequests + '\n' +
        "Max open requests     : " + stats.maxOpenRequests + '\n' +
        "Total connections     : " + stats.totalConnections + '\n' +
        "Open connections      : " + stats.openConnections + '\n' +
        "Max open connections  : " + stats.maxOpenConnections + '\n' +
        "Requests timed out    : " + stats.requestTimeouts + '\n' +
        "Connections timed out : " + stats.idleTimeouts + '\n'
      }
      case Left(ex) => ctx.complete(500, "Couldn't get server stats due to " + ex.getMessage)
    }
  }

  def in[U](duration: Duration)(body: => U) {
    actorSystem.scheduler.scheduleOnce(duration, new Runnable { def run() { body } })
  }

  lazy val largeTempFile = {
    val file = File.createTempFile("streamingTest", ".txt")
    FileUtils.writeAllText((1 to 1000).map("This is line " + _).mkString("\n"), file)
    file.deleteOnExit()
    file
  }

  lazy val httpServer = actorSystem.actorFor("user/http-server")

}