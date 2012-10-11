package docs

import org.specs2.mutable.Specification
import cc.spray.testkit.Specs2RouteTest
import cc.spray.routing.HttpService
import akka.actor.Actor


class RejectionHandlerExamplesSpec extends Specification with Specs2RouteTest with HttpService {
  implicit def actorRefFactory = system

  //# example-1
  import cc.spray.routing.RejectionHandler
  import cc.spray.routing.MissingCookieRejection
  import cc.spray.http.HttpResponse
  import cc.spray.http.StatusCodes._

  implicit val myRejectionHandler = RejectionHandler.fromPF {
    case MissingCookieRejection(cookieName) :: _ =>
      HttpResponse(BadRequest, "No cookies, no service!!!")
  }

  class MyService extends Actor with HttpService {
    def actorRefFactory = context

    def receive = runRoute {
      `<my-route-definition>`
    }
  }
  //#

  def `<my-route-definition>`: cc.spray.routing.Route = null

  "example" in {
    Get() ~> sealRoute(reject(MissingCookieRejection("abc"))) ~> check {
      entityAs[String] === "No cookies, no service!!!"
    }
  }

}
