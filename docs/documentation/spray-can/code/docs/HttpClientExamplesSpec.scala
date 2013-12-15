package docs

import org.specs2.mutable.Specification
import spray.testkit.Specs2Utils._
import org.specs2.time.NoTimeConversions

class HttpClientExamplesSpec extends Specification with NoTimeConversions {

  "sslengine-config" in compileOnly {
    import spray.io.ClientSSLEngineProvider

    implicit val myEngineProvider = ClientSSLEngineProvider { engine =>
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      engine
    }
  }

  "request-level-example" in compileOnly {
    import scala.concurrent.Future
    import scala.concurrent.duration._

    import akka.actor.ActorSystem
    import akka.util.Timeout
    import akka.pattern.ask
    import akka.io.IO

    import spray.can.Http
    import spray.http._
    import HttpMethods._

    implicit val system: ActorSystem = ActorSystem()
    implicit val timeout: Timeout = Timeout(15.seconds)
    import system.dispatcher // implicit execution context

    val response: Future[HttpResponse] =
      (IO(Http) ? HttpRequest(GET, Uri("http://spray.io"))).mapTo[HttpResponse]

    // or, with making use of spray-httpx
    import spray.httpx.RequestBuilding._

    val response2: Future[HttpResponse] =
      (IO(Http) ? Get("http://spray.io")).mapTo[HttpResponse]
  }

}
