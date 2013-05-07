package spray.examples

import scala.concurrent.duration._
import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import spray.http._
import HttpMethods._
import StatusCodes._
import spray.http.MediaTypes._
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.json._

object Main extends App {

  implicit val system = ActorSystem()

  // the handler actor replies to incoming HttpRequests
  val handler = system.actorOf(Props[BenchmarkService], name = "handler")

  val interface = system.settings.config.getString("app.interface")
  val port = system.settings.config.getInt("app.port")
  IO(Http) ! Http.Bind(handler, interface, port)
}

class BenchmarkService extends Actor {
  import context.dispatcher // ExecutionContext for scheduler

  def fastPath: Http.FastPath = {
    case HttpRequest(GET, Uri.Path("/fast-ping"), _, _, _) => HttpResponse(entity = "FAST-PONG!")
    case HttpRequest(GET, Uri.Path("/fast-json"), _, _, _) =>
      HttpResponse(entity = HttpEntity(`application/json`,
        JsObject("fast-sprayed-message" -> JsString("Hello, World!")).compactPrint.getBytes("ASCII")))
  }

  def receive = {
    // when a new connection comes in we register ourselves as the connection handler
    case _: Http.Connected => sender ! Http.Register(self, fastPath = fastPath)

    case HttpRequest(GET, Uri.Path("/"), _, _, _) => sender ! HttpResponse(
      entity = HttpEntity(MediaTypes.`text/html`,
        <html>
          <body>
            <h1>Tiny <i>spray-can</i> benchmark server</h1>
            <p>Defined resources:</p>
            <ul>
              <li><a href="/ping">/ping</a></li>
              <li><a href="/fast-ping">/fast-ping</a></li>
              <li><a href="/stop">/stop</a></li>
            </ul>
          </body>
        </html>.toString()
      )
    )

    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) => sender ! HttpResponse(entity = "PONG!")

    case HttpRequest(GET, Uri.Path("/json"), _, _, _) =>
      sender ! HttpResponse(entity = HttpEntity(`application/json`,
        JsObject("sprayed-message" -> JsString("Hello, World!")).compactPrint.getBytes("ASCII")))

    case HttpRequest(GET, Uri.Path("/stop"), _, _, _) =>
      sender ! HttpResponse(entity = "Shutting down in 1 second ...")
      context.system.scheduler.scheduleOnce(1.second) { context.system.shutdown() }

    case _: HttpRequest => sender ! HttpResponse(NotFound, entity = "Unknown resource!")
  }
}
