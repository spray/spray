package spray.client

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import com.typesafe.config.ConfigFactory
import akka.actor.{ ActorRef, Actor, Props, ActorSystem }
import spray.can.Http
import spray.http._
import spray.http.HttpHeaders.Location
import akka.io.IO
import spray.http.StatusCodes._
import akka.pattern.ask
import spray.util._
import scala.concurrent.duration._
import akka.util.Timeout
import java.net.InetSocketAddress

/**
 * Date: 05/10/2013
 * Time: 22:17
 */
class RedirectionIntegrationSpec extends Specification with NoTimeConversions {
  sequential

  val testConf = ConfigFactory.parseString("""
    akka {
      event-handlers = ["akka.testkit.TestEventListener"]
      loglevel = WARNING
    }
    spray.can {
      host-connector {
        max-redirects = 5
      }
      server.remote-address-header = on
      client.proxy.http = none
    }""")
  implicit val system = ActorSystem(Utils.actorSystemNameFrom(getClass), testConf)
  import system.dispatcher
  val (interfaceA, portA) = Utils.temporaryServerHostnameAndPort()
  val (interfaceB, portB) = Utils.temporaryServerHostnameAndPort()

  implicit val timeout = Timeout(10.seconds)

  val connectA = Http.Connect(interfaceA, portA)
  val transport = IO(Http)(spray.util.actorSystem)

  step {
    val serviceA = system.actorOf {
      Props {
        new Actor {
          def receive = {
            case Http.Connected(peer, _) ⇒
              val perConnection = context.actorOf(Props(new PerConnectionActor(peer)))
              sender ! Http.Register(perConnection)
          }

          class PerConnectionActor(peer: InetSocketAddress) extends Actor {
            def receive = {
              case x: HttpRequest if x.uri.path.toString == "/redirect-rel" ⇒ sender ! redirectRel("/foo/" + peer.getPort)
              case x: HttpRequest if x.uri.path.toString == "/redirect-abs" ⇒ sender ! redirectAbs(interfaceB, portB, "/foo")
              case x: HttpRequest if x.uri.path.toString == "/base/redirect-rel-dot" ⇒ sender ! redirectRel("./foo/../bar")
              case x: HttpRequest if x.uri.path.toString == "/redirect-inf" ⇒ sender ! redirectRel("/redirect-inf")
              case x: HttpRequest if x.uri.path.toString == s"/foo/${peer.getPort}" ⇒ sender ! HttpResponse(entity = "sameConnection")
              case x: HttpRequest ⇒ sender ! HttpResponse(entity = "service-a" + x.uri.path.toString)
              case _: Http.ConnectionClosed ⇒ context.stop(self)
            }
          }
        }
      }
    }
    IO(Http).ask(Http.Bind(serviceA, interfaceA, portA))(3.seconds).await

    val serviceB = system.actorOf {
      Props {
        new Actor {
          def receive = {
            case x: Http.Connected        ⇒ sender ! Http.Register(self)
            case x: HttpRequest           ⇒ sender ! HttpResponse(entity = "service-b" + x.uri.path.toString)
            case _: Http.ConnectionClosed ⇒ // ignore
          }
        }
      }
    }
    IO(Http).ask(Http.Bind(serviceB, interfaceB, portB))(3.seconds).await
  }

  def redirectRel(path: String) =
    HttpResponse(status = PermanentRedirect, headers = Location(Uri(s"$path")) :: Nil)

  def redirectAbs(interface: String, port: Int, path: String) =
    HttpResponse(status = PermanentRedirect, headers = Location(Uri(s"http://$interface:$port$path")) :: Nil)

  "An HttpDialog" should {
    "follow relative redirect responses and reuse connection" in {
      HttpDialog(transport)
        .send(HttpRequest(uri = s"http://$interfaceA:$portA/redirect-rel"))
        .end
        .map(_.entity.asString)
        .await === "sameConnection"
    }
    "follow absolute redirect responses to other hosts" in {
      HttpDialog(transport)
        .send(HttpRequest(uri = s"http://$interfaceA:$portA/redirect-abs"))
        .end
        .map(_.entity.asString)
        .await === "service-b/foo"
    }
    "follow relative redirect responses with dot (./ and  ../) notation" in {
      HttpDialog(transport)
        .send(HttpRequest(uri = s"http://$interfaceA:$portA/base/redirect-rel-dot"))
        .end
        .map(_.entity.asString)
        .await === "service-a/base/bar"
    }
    "NOT redirect infinitely" in {
      val uri = s"http://$interfaceA:$portA/redirect-inf"
      HttpDialog(transport)
        .send(HttpRequest(uri = uri))
        .end
        .map(_.status)
        .await === PermanentRedirect
    }
  }

  step {
    system.shutdown()
  }
}
