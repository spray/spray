package docs

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService._
import spray.routing.MissingCookieRejection

object MyRejectionHandler {
  //# example-1
  import spray.routing._
  import spray.http._
  import StatusCodes._
  import Directives._

  implicit val myRejectionHandler = RejectionHandler {
    case MissingCookieRejection(cookieName) :: _ =>
      complete(BadRequest, "No cookies, no service!!!")
  }

  class MyService extends HttpServiceActor {
    def receive = runRoute {
      `<my-route-definition>`
    }
  }
  //#

  def `<my-route-definition>`: spray.routing.Route = null
}

class RejectionHandlerExamplesSpec extends Specification with Specs2RouteTest {
  import MyRejectionHandler._

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
    success // hide
  }

}
