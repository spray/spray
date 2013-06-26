package docs.directives

import spray.http.StatusCodes._
import spray.http.HttpHeaders._

class HeaderDirectivesExamplesSpec extends DirectivesSpec {
  "example-1" in {
    val route =
      headerValueByName("X-User-Id") { userId =>
        complete(s"The user is $userId")
      }

    Get("/") ~> RawHeader("X-User-Id", "Joe42") ~> route ~> check {
      entityAs[String] === "The user is Joe42"
    }

    Get("/") ~> sealRoute(route) ~> check {
      status === BadRequest
      entityAs[String] === "Request is missing required HTTP header 'X-User-Id'"
    }
  }
}
