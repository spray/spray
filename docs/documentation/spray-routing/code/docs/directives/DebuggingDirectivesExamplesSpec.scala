package docs.directives

import akka.event.{LoggingAdapter, Logging}
import spray.routing.directives.{LoggingMagnet, LogEntry, DebuggingDirectives}
import spray.http.{HttpMessageStart, HttpResponsePart, HttpResponse, HttpRequest}
import spray.util.LoggingContext

class DebuggingDirectivesExamplesSpec extends DirectivesSpec {
  implicit val la: LoggingContext = null
  "logRequest-0" in {
    // different possibilities of using logRequest

    // The first alternatives use an implicitly available LoggingContext for logging
    // marks with "get-user", log with debug level, HttpRequest.toString
    DebuggingDirectives.logRequest("get-user")

    // marks with "get-user", log with info level, HttpRequest.toString
    DebuggingDirectives.logRequest("get-user", Logging.InfoLevel)

    // logs just the request method at debug level
    def requestMethod(req: HttpRequest): String = req.method.toString
    DebuggingDirectives.logRequest(requestMethod _)

    // logs just the request method at info level
    def requestMethodAsInfo(req: HttpRequest): LogEntry = LogEntry(req.method.toString, Logging.InfoLevel)
    DebuggingDirectives.logRequest(requestMethodAsInfo _)

    // This one doesn't use the implicit LoggingContext but uses `println` for logging
    def printRequestMethod(req: HttpRequest): Unit = println(req.method)
    val logRequestPrintln = DebuggingDirectives.logRequest(LoggingMagnet(printRequestMethod))

    Get("/") ~> logRequestPrintln(complete("logged")) ~> check {
      responseAs[String] === "logged"
    }
  }
  "logRequestResponse" in {
    // different possibilities of using logRequestResponse

    // The first alternatives use an implicitly available LoggingContext for logging
    // marks with "get-user", log with debug level, HttpRequest.toString, HttpResponse.toString
    DebuggingDirectives.logRequestResponse("get-user")

    // marks with "get-user", log with info level, HttpRequest.toString, HttpResponse.toString
    DebuggingDirectives.logRequestResponse("get-user", Logging.InfoLevel)

    // logs just the request method and response status at info level
    def requestMethodAndResponseStatusAsInfo(req: HttpRequest): Any => Option[LogEntry] = {
      case res: HttpResponse => Some(LogEntry(req.method + ":" + res.message.status, Logging.InfoLevel))
      case _ => None // other kind of responses
    }
    DebuggingDirectives.logRequestResponse(requestMethodAndResponseStatusAsInfo _)

    // This one doesn't use the implicit LoggingContext but uses `println` for logging
    def printRequestMethodAndResponseStatus(req: HttpRequest)(res: Any): Unit =
      println(requestMethodAndResponseStatusAsInfo(req)(res).map(_.obj.toString).getOrElse(""))
    val logRequestResponsePrintln = DebuggingDirectives.logRequestResponse(LoggingMagnet(printRequestMethodAndResponseStatus))

    Get("/") ~> logRequestResponsePrintln(complete("logged")) ~> check {
      responseAs[String] === "logged"
    }
  }
  "logResponse" in {
    // different possibilities of using logResponse

    // The first alternatives use an implicitly available LoggingContext for logging
    // marks with "get-user", log with debug level, HttpResponse.toString
    DebuggingDirectives.logResponse("get-user")

    // marks with "get-user", log with info level, HttpResponse.toString
    DebuggingDirectives.logResponse("get-user", Logging.InfoLevel)

    // logs just the response status at debug level
    def responseStatus(res: Any): String = res match {
      case x: HttpResponse => x.status.toString
      case _ => "unknown response part"
    }
    DebuggingDirectives.logResponse(responseStatus _)

    // logs just the response status at info level
    def responseStatusAsInfo(res: Any): LogEntry = LogEntry(responseStatus(res), Logging.InfoLevel)
    DebuggingDirectives.logResponse(responseStatusAsInfo _)

    // This one doesn't use the implicit LoggingContext but uses `println` for logging
    def printResponseStatus(res: Any): Unit = println(responseStatus(res))
    val logResponsePrintln = DebuggingDirectives.logResponse(LoggingMagnet(printResponseStatus))

    Get("/") ~> logResponsePrintln(complete("logged")) ~> check {
      responseAs[String] === "logged"
    }
  }
}
