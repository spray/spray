package docs.directives

import spray.http.StatusCodes._
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import spray.util.LoggingContext
import spray.routing.{PathMatchers, ExceptionHandler}
import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import spray.routing.directives.NameReceptacle

class FutureDirectivesExamplesSpec extends DirectivesSpec {
  object TestException extends Throwable

  implicit def myExceptionHandler(implicit log: LoggingContext) =
    ExceptionHandler {
      case TestException => ctx =>
        ctx.complete(InternalServerError, "Unsuccessful future!")
    }

  val resourceActor = system.actorOf(Props(new Actor {
    def receive = { case _ => sender() ! "resource" }
  }))
  implicit val responseTimeout = Timeout(2, TimeUnit.SECONDS)

  "example-1" in {
    def divide(a: Int, b: Int): Future[Int] = Future {
      a / b
    }

    val route =
      path("divide" / IntNumber / IntNumber) { (a, b) =>
        onComplete(divide(a, b)) {
          case Success(value) => complete(s"The result was $value")
          case Failure(ex)    => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
        }
      }

    Get("/divide/10/2") ~> route ~> check {
      responseAs[String] === "The result was 5"
    }

    Get("/divide/10/0") ~> sealRoute(route) ~> check {
      status === InternalServerError
      responseAs[String] === "An error occurred: / by zero"
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
      responseAs[String] === "Ok"
    }

    Get("/failure") ~> sealRoute(route) ~> check {
      status === InternalServerError
      responseAs[String] === "Unsuccessful future!"
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
      responseAs[String] === "Ok"
    }

    Get("/failure") ~> sealRoute(route) ~> check {
      status === InternalServerError
      responseAs[String] === "Unsuccessful future!"
    }
  }
}
