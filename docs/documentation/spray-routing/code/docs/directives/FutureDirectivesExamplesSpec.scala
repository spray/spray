package docs.directives

import spray.http.StatusCodes._
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import spray.util.LoggingContext
import spray.routing.ExceptionHandler

class FutureDirectivesExamplesSpec extends DirectivesSpec {
  object TestException extends Throwable

  implicit def myExceptionHandler(implicit log: LoggingContext) =
    ExceptionHandler {
      case TestException => ctx =>
        ctx.complete(InternalServerError, "Unsuccessful future!")
    }

  def handleResponse(response: Try[String]) = response match {
    case Success(value) => complete(value)
    case Failure(ex)    => failWith(ex)
  }

  "example-1" in {
    val route =
      path("success") {
        onComplete(Future { "Ok" }) {
          handleResponse
        }
      } ~
      path("failure") {
        onComplete(Future.failed[String](TestException)) {
          handleResponse
        }
      }

    Get("/success") ~> route ~> check {
      entityAs[String] === "Ok"
    }

    Get("/failure") ~> sealRoute(route) ~> check {
      status === InternalServerError
      entityAs[String] === "Unsuccessful future!"
    }
  }

  "example-2" in {
    val route =
      path("success") {
        onSuccess(Future { "Ok" }) { extraction =>
          complete(extraction)
        }
      } ~
      path("failure") {
        onSuccess(Future.failed[String](TestException)) { extraction =>
          complete(extraction)
        }
      }


    Get("/success") ~> route ~> check {
      entityAs[String] === "Ok"
    }

    Get("/failure") ~> sealRoute(route) ~> check {
      status === InternalServerError
      entityAs[String] === "Unsuccessful future!"
    }
  }

  "example-3" in {
    val route =
      path("success") {
        onFailure(Future { "Ok" }) { extraction =>
          failWith(extraction) // not executed.
        }
      } ~
        path("failure") {
          onFailure(Future.failed[String](TestException)) { extraction =>
            failWith(extraction)
          }
        }


    Get("/success") ~> route ~> check {
      entityAs[String] === "Ok"
    }

    Get("/failure") ~> sealRoute(route) ~> check {
      status === InternalServerError
      entityAs[String] === "Unsuccessful future!"
    }
  }
}
