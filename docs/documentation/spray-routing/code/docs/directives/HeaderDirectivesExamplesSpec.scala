package docs.directives

import spray.http.StatusCodes._
import spray.http.HttpHeaders._
import spray.http.{HttpOrigin, HttpHeader}
import spray.routing.MissingHeaderRejection

class HeaderDirectivesExamplesSpec extends DirectivesSpec {
  "headerValueByName-0" in {
    val route =
      headerValueByName("X-User-Id") { userId =>
        complete(s"The user is $userId")
      }

    Get("/") ~> RawHeader("X-User-Id", "Joe42") ~> route ~> check {
      responseAs[String] === "The user is Joe42"
    }

    Get("/") ~> sealRoute(route) ~> check {
      status === BadRequest
      responseAs[String] === "Request is missing required HTTP header 'X-User-Id'"
    }
  }
  "headerValue-0" in {
    def extractHostPort: HttpHeader => Option[Int] = {
      case h: `Host`=> Some(h.port)
      case x => None
    }

    val route =
      headerValue(extractHostPort) { port =>
        complete(s"The port was $port")
      }

    Get("/") ~> Host("example.com", 5043) ~> route ~> check {
      responseAs[String] === "The port was 5043"
    }
    Get("/") ~> sealRoute(route) ~> check {
      status === NotFound
      responseAs[String] === "The requested resource could not be found."
    }
  }
  "headerValuePF-0" in {
    def extractHostPort: PartialFunction[HttpHeader, Int] = {
      case h: `Host`=> h.port
    }

    val route =
      headerValuePF(extractHostPort) { port =>
        complete(s"The port was $port")
      }

    Get("/") ~> Host("example.com", 5043) ~> route ~> check {
      responseAs[String] === "The port was 5043"
    }
    Get("/") ~> sealRoute(route) ~> check {
      status === NotFound
      responseAs[String] === "The requested resource could not be found."
    }
  }
  "headerValueByType-0" in {
    val route =
      headerValueByType[Origin]() { origin ⇒
        complete(s"The first origin was ${origin.originList.head}")
      }

    val originHeader = Origin(Seq(HttpOrigin("http://localhost:8080")))

    // extract a header if the type is matching
    Get("abc") ~> originHeader ~> route ~> check {
      responseAs[String] === "The first origin was http://localhost:8080"
    }

    // reject a request if no header of the given type is present
    Get("abc") ~> route ~> check {
      rejection must beLike { case MissingHeaderRejection("Origin") ⇒ ok }
    }
  }
  "optionalHeaderValueByType-0" in {
    val route =
      optionalHeaderValueByType[Origin]() {
        case Some(origin) ⇒ complete(s"The first origin was ${origin.originList.head}")
        case None         ⇒ complete("No Origin header found.")
      }

    val originHeader = Origin(Seq(HttpOrigin("http://localhost:8080")))
    // extract Some(header) if the type is matching
    Get("abc") ~> originHeader ~> route ~> check {
      responseAs[String] === "The first origin was http://localhost:8080"
    }

    // extract None if no header of the given type is present
    Get("abc") ~> route ~> check {
      responseAs[String] === "No Origin header found."
    }
  }
}
