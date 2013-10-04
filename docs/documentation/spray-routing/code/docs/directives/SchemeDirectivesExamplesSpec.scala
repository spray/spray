package docs.directives

import spray.http.Uri
import spray.http.StatusCodes._
import spray.http.HttpHeaders._

class SchemeDirectivesExamplesSpec extends DirectivesSpec {
  "example-1" in {
    val route =
      schemeName { scheme =>
        complete(s"The scheme is '${scheme}'")
      }

    Get("https://www.example.com/") ~> route ~> check {
      responseAs[String] === "The scheme is 'https'"
    }
  }

  "example-2" in {
    val route =
      scheme("http") {
        extract(_.request.uri) { uri ⇒
          redirect(uri.copy(scheme = "https"), MovedPermanently)
        }
      } ~
      scheme("https") {
        complete(s"Safe and secure!")
      }

    Get("http://www.example.com/hello") ~> route ~> check {
      status === MovedPermanently
      header[Location] === Some(Location(Uri("https://www.example.com/hello")))
    }

    Get("https://www.example.com/hello") ~> route ~> check {
      responseAs[String] === "Safe and secure!"
    }
  }
}
