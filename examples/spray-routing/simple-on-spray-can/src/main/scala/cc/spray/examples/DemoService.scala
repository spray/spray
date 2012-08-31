package cc.spray.examples

import java.io.File
import org.parboiled.common.FileUtils
import akka.util.Duration
import akka.util.duration._
import akka.actor.{ActorLogging, Props, Actor}
import akka.pattern.ask
import cc.spray.routing.{HttpService, RequestContext}
import cc.spray.routing.directives.CachingDirectives
import cc.spray.can.server.HttpServer
import cc.spray.httpx.encoding.Gzip
import cc.spray.http._
import MediaTypes._
import CachingDirectives._


// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class DemoServiceActor extends Actor with DemoService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  def receive = runRoute(demoRoute)
}


// this trait defines our service behavior independently from the service actor
trait DemoService extends HttpService {

  val demoRoute = {
    get {
      path("") {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete(index)
        }
      } ~
      path("ping") {
        complete("PONG!")
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
        cache(simpleRouteCache) { ctx =>
          in(1500.millis) {
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

  lazy val simpleRouteCache = routeCache()

  lazy val index =
    <html>
      <body>
        <h1>Say hello to <i>spray-routing</i> on <i>spray-can</i>!</h1>
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
    actorRefFactory.actorOf(
      Props {
        new Actor with ActorLogging {
          var remainingChunks = 16
          def receive = {
            case 'Start =>
              // we prepend 2048 "empty" bytes to push the browser to immediately start displaying the incoming chunks
              val htmlStart = " " * 2048 + "<html><body><h2>A streaming response</h2><p>(for 15 seconds)<ul>"
              ctx.handler ! ChunkedResponseStart(HttpResponse(entity = HttpBody(`text/html`, htmlStart)))
            case _: HttpServer.AckSend if remainingChunks > 0 =>
              // we use the successful sending of a chunk as trigger for scheduling the next chunk
              remainingChunks -= 1
              in(500.millis) {
                ctx.handler ! MessageChunk("<li>" + DateTime.now.toIsoDateTimeString + "</li>")
              }
            case _: HttpServer.AckSend =>
              ctx.handler ! MessageChunk("</ul><p>Finished.</p></body></html>")
              ctx.handler ! ChunkedMessageEnd()
              context.stop(self)
            case HttpServer.Closed(_, reason) =>
              log.warning("Stopping response streaming due to {}", reason)
          }
        }
      }, "streaming-actor"
    ) ! 'Start
  }

  def showServerStats(ctx: RequestContext) {
    actorRefFactory.actorFor("../http-server")
      .ask(HttpServer.GetStats)(1.second)
      .mapTo[HttpServer.Stats]
      .onComplete {
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

}