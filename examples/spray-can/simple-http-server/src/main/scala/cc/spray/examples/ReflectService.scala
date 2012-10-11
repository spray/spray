package cc.spray.examples

import akka.actor.{ActorLogging, Actor}
import cc.spray.http.{HttpBody, HttpHeaders, HttpResponse, HttpRequest}
import cc.spray.http.HttpMethods._
import cc.spray.http.HttpHeaders.RawHeader
import cc.spray.http.MediaTypes._
import cc.spray.http.HttpHeaders.RawHeader
import cc.spray.http.HttpResponse

class ReflectService extends Actor with ActorLogging {
  def receive = {
    case HttpRequest(GET, "/", _, _, _) =>
      sender ! HttpResponse(entity = indexBody,
        headers = List(RawHeader("Alternate-Protocol", "443:npn-spdy/2")))
  }

  lazy val indexBody =
    HttpBody(`text/html`,
      <html>
        <body>
          <h1>Say hello to <i>spray-can</i>!</h1>
          <p>Defined resources:</p>
          <ul>
            <li><a href="/ping">/ping</a></li>
            <li><a href="/stream">/stream</a></li>
            <li><a href="/stats">/stats</a></li>
            <li><a href="/crash">/crash</a></li>
            <li><a href="/timeout">/timeout</a></li>
            <li><a href="/timeout/timeout">/timeout/timeout</a></li>
            <li><a href="/stop">/stop</a></li>
          </ul>
        </body>
      </html>.toString
    )
}
