package docs

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService._
import spray.routing.Route

object MyHandler {
  //# example-1
  import spray.util.LoggingContext
  import spray.http.StatusCodes._
  import spray.routing._

  implicit def myExceptionHandler(implicit log: LoggingContext) =
    ExceptionHandler {
      case e: ArithmeticException => ctx =>
        log.warning("Request {} could not be handled normally", ctx.request)
        ctx.complete(InternalServerError, "Bad numbers, bad result!!!")
    }

  class MyService extends HttpServiceActor {
    def receive = runRoute {
      `<my-route-definition>`
    }
  }
  //#

  def `<my-route-definition>`: Route = null
}

class ExceptionHandlerExamplesSpec extends Specification with Specs2RouteTest {
  import MyHandler._

  "example" in {
    Get() ~> sealRoute(Route(ctx => 1 / 0)) ~> check {
      responseAs[String] === "Bad numbers, bad result!!!"
    }
  }

}
