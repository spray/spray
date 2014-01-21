package docs

import org.specs2.mutable.Specification
import akka.actor.{ActorSystem, ActorRef, Actor}
import spray.testkit.Specs2Utils._
import spray.http._
import HttpMethods._

class HttpServerExamplesSpec extends Specification {

  class Actor1 extends Actor {
    //# simple-reply
    def receive = {
      case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
        sender() ! HttpResponse(entity = "PONG")
    }
    //#
  }

  "bind-example" in compileOnly {
    import akka.io.IO
    import spray.can.Http

    implicit val system = ActorSystem()

    val myListener: ActorRef = // ...
      null // hide

    IO(Http) ! Http.Bind(myListener, interface = "localhost", port = 8080)
  }

  class Actor2 extends Actor {
    //# acked-reply
    def receive = {
      case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
        sender() ! HttpResponse(entity = "PONG").withAck("ok")

      case "ok" => println("Response was sent successfully")
    }
    //#
  }

  "sslengine-config" in compileOnly {
    import spray.io.ServerSSLEngineProvider

    implicit val myEngineProvider = ServerSSLEngineProvider { engine =>
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      engine
    }
  }

  "sslcontext-provision" in compileOnly {
    import javax.net.ssl.SSLContext

    implicit val mySSLContext: SSLContext = {
      val context = SSLContext.getInstance("TLS")
      // context.init(...)
      context
    }
  }
}
