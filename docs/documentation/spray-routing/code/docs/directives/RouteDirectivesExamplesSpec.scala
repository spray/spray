package docs.directives

import spray.http.{RequestProcessingException, HttpResponse, StatusCodes}
import spray.routing.ValidationRejection

class RouteDirectivesExamplesSpec extends DirectivesSpec {

  "complete-examples" in {
    val route =
      path("a") {
        complete(HttpResponse(entity = "foo"))
      } ~
      path("b") {
        complete(StatusCodes.Created, "bar")
      } ~
      (path("c") & complete("baz")) // `&` also works with `complete` as the 2nd argument

    Get("/a") ~> route ~> check {
      status === StatusCodes.OK
      responseAs[String] === "foo"
    }

    Get("/b") ~> route ~> check {
      status === StatusCodes.Created
      responseAs[String] === "bar"
    }

    Get("/c") ~> route ~> check {
      status === StatusCodes.OK
      responseAs[String] === "baz"
    }
  }

  "reject-examples" in {
    val route =
      path("a") {
        reject // don't handle here, continue on
      } ~
      path("a") {
        complete("foo")
      } ~
      path("b") {
        // trigger a ValidationRejection explicitly
        // rather than through the `validate` directive
        reject(ValidationRejection("Restricted!"))
      }

    Get("/a") ~> route ~> check {
      responseAs[String] === "foo"
    }

    Get("/b") ~> route ~> check {
      rejection === ValidationRejection("Restricted!")
    }
  }

  "redirect-examples" in {
    val route =
      pathPrefix("foo") {
        pathSingleSlash {
          complete("yes")
        } ~
        pathEnd {
          redirect("/foo/", StatusCodes.PermanentRedirect)
        }
      }

    Get("/foo/") ~> route ~> check {
      responseAs[String] === "yes"
    }

    Get("/foo") ~> route ~> check {
      status === StatusCodes.PermanentRedirect
      responseAs[String] === """The request, and all future requests should be repeated using <a href="/foo/">this URI</a>."""
    }
  }

  "failwith-examples" in {
    val route =
      path("foo") {
        failWith(new RequestProcessingException(StatusCodes.BandwidthLimitExceeded))
      }

    Get("/foo") ~> sealRoute(route) ~> check {
      status === StatusCodes.BandwidthLimitExceeded
      responseAs[String] === "Bandwidth limit has been exceeded."
    }
  }

}
