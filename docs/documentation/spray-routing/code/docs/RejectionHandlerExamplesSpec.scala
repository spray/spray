package docs

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService
import akka.actor.Actor


class RejectionHandlerExamplesSpec extends Specification with Specs2RouteTest with HttpService {
  implicit def actorRefFactory = system

  //# example-1
  import spray.routing._
  import spray.http._
  import StatusCodes._

  implicit val myRejectionHandler = RejectionHandler.fromPF {
    case MissingCookieRejection(cookieName) :: _ =>
      HttpResponse(BadRequest, "No cookies, no service!!!")
  }

  class MyService extends Actor with HttpServiceActor {
    def receive = runRoute {
      `<my-route-definition>`
    }
  }
  //#

  def `<my-route-definition>`: spray.routing.Route = null

  "example" in {
    Get() ~> sealRoute(reject(MissingCookieRejection("abc"))) ~> check {
      entityAs[String] === "No cookies, no service!!!"
    }
  }

  "example-2" in {
    import spray.httpx.encoding._

    val route =
      path("order") {
        get {
          complete("Received GET")
        } ~
        post {
          decodeRequest(Gzip) {
            complete("Received POST")
          }
        }
      }
    success //
  }

}
