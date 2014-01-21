package docs

import scala.concurrent.duration.Duration
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import akka.testkit.{TestProbe, TestActorRef}
import akka.actor.Actor


class TimeoutHandlingExamplesSpec extends Specification with Specs2RouteTest {

  //# example-1
  import spray.http._
  import spray.routing._

  class MyService extends HttpServiceActor {
    val system = 0 // shadow implicit from test, hide
    def receive = handleTimeouts orElse runRoute(myRoute)

    def myRoute: Route = `<my-route-definition>`

    def handleTimeouts: Receive = {
      case Timedout(x: HttpRequest) =>
        sender() ! HttpResponse(StatusCodes.InternalServerError, "Too late")
    }
  }
  //#

  def `<my-route-definition>`: Route = null

  "example" in {
    import spray.httpx.unmarshalling._
    val probe = TestProbe()
    val service = TestActorRef(new MyService)
    probe.send(service, Timedout(HttpRequest()))
    val HttpResponse(status, entity, _, _) = probe.receiveOne(Duration.Zero)
    status === StatusCodes.InternalServerError
    entity.as[String] === Right("Too late")
  }

}
