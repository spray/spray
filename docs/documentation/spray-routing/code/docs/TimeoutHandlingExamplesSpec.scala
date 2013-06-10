package docs

import org.specs2.mutable.Specification
import spray.testkit.Specs2Utils.compileOnly

class TimeoutHandlingExamplesSpec extends Specification {

  "example-1" in compileOnly {
    import spray.http._
    import spray.routing._

    class MyService extends HttpServiceActor {
      def receive = handleTimeouts orElse runRoute(myRoute)

      def myRoute: Route = // ...
        null // hide

      def handleTimeouts: Receive = {
        case Timedout(x: HttpRequest) =>
          sender ! HttpResponse(StatusCodes.InternalServerError, "Too late")
      }
    }
  }
}
