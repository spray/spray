package docs.directives

import spray.routing.{RejectionHandler, ExceptionHandler, Rejection, Directive1}
import spray.http.StatusCodes

class ExecutionDirectivesExamplesSpec extends DirectivesSpec {
  import scala.concurrent.ExecutionContext.global
  "detach-0" in {
    val route =
      detach() {
        complete("Result") // route executed in future
      }
    Get("/") ~> route ~> check {
      responseAs[String] === "Result"
    }
  }
  "detach-1" in {
    /// / a custom directive to extract the id of the current thread
    def currentThreadId: Directive1[Long] = extract(_ => Thread.currentThread().getId)
    val route =
      currentThreadId { originThread =>
        path("rejectDetached") {
          detach() {
            reject()
          }
        } ~
        path("reject") {
          reject()
        } ~
        currentThreadId { alternativeThread =>
          complete(s"$originThread,$alternativeThread")
        }
      }

    Get("/reject") ~> route ~> check {
      val Array(original, alternative) = responseAs[String].split(",")
      original === alternative
    }
    Get("/rejectDetached") ~> route ~> check {
      val Array(original, alternative) = responseAs[String].split(",")
      original !== alternative
    }
  }
  "handleExceptions" in {
    val divByZeroHandler = ExceptionHandler {
      case _: ArithmeticException => complete(StatusCodes.BadRequest, "You've got your arithmetic wrong, fool!")
    }
    val route =
      path("divide" / IntNumber / IntNumber) { (a, b) =>
        handleExceptions(divByZeroHandler) {
          complete(s"The result is ${a / b}")
        }
      }

    Get("/divide/10/5") ~> route ~> check {
      responseAs[String] === "The result is 2"
    }
    Get("/divide/10/0") ~> route ~> check {
      status === StatusCodes.BadRequest
      responseAs[String] === "You've got your arithmetic wrong, fool!"
    }
  }
  "handleRejections" in {
    val totallyMissingHandler = RejectionHandler {
      case Nil /* secret code for path not found */ =>
        complete(StatusCodes.NotFound, "Oh man, what you are looking for is long gone.")
    }
    val route =
      pathPrefix("handled") {
        handleRejections(totallyMissingHandler) {
          path("existing")(complete("This path exists"))
        }
      }

    Get("/handled/existing") ~> route ~> check {
      responseAs[String] === "This path exists"
    }
    Get("/missing") ~> sealRoute(route) /* applies default handler */ ~> check {
      status === StatusCodes.NotFound
      responseAs[String] === "The requested resource could not be found."
    }
    Get("/handled/missing") ~> route ~> check {
      status === StatusCodes.NotFound
      responseAs[String] === "Oh man, what you are looking for is long gone."
    }
  }
}
