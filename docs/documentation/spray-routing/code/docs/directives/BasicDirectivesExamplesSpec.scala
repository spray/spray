package docs.directives

import spray.http._
import spray.routing._
import akka.testkit._
import spray.http.HttpRequest
import spray.routing.Rejected
import spray.routing.MalformedRequestContentRejection
import spray.http.HttpHeaders.RawHeader
import spray.http.ChunkedResponseStart
import spray.http.HttpResponse
import scala.util.control.NonFatal
import scala.concurrent.duration._

class BasicDirectivesExamplesSpec extends DirectivesSpec {
  "0extract" in {
    val uriLength = extract(_.request.uri.toString.length)
    val route =
      uriLength { len =>
        complete(s"The length of the request URI is $len")
      }

    Get("/abcdef") ~> route ~> check {
      responseAs[String] === "The length of the request URI is 25"
    }
  }
  "hextract" in {
    import shapeless.HNil
    val pathAndQuery = hextract { ctx =>
      val uri = ctx.request.uri
      uri.path :: uri.query :: HNil
    }
    val route =
      pathAndQuery { (p, query) =>
        complete(s"The path is $p and the query is $query")
      }

    Get("/abcdef?ghi=12") ~> route ~> check {
      responseAs[String] === "The path is /abcdef and the query is ghi=12"
    }
  }
  "hprovide" in {
    import shapeless.HNil
    def provideStringAndLength(value: String) = hprovide(value :: value.length :: HNil)
    val route =
      provideStringAndLength("test") { (value, len) =>
        complete(s"Value is $value and its length is $len")
      }
    Get("/") ~> route ~> check {
      responseAs[String] === "Value is test and its length is 4"
    }
  }
  "0mapHttpResponse" in {
    def overwriteResultStatus(response: HttpResponse): HttpResponse =
      response.copy(status = StatusCodes.BadGateway)
    val route = mapHttpResponse(overwriteResultStatus)(complete("abc"))

    Get("/abcdef?ghi=12") ~> route ~> check {
      status === StatusCodes.BadGateway
    }
  }
  "mapHttpResponseEntity" in {
    def prefixEntity(entity: HttpEntity): HttpEntity =
      HttpEntity(HttpData("test") +: entity.data)
    val prefixWithTest: Directive0 = mapHttpResponseEntity(prefixEntity)
    val route = prefixWithTest(complete("abc"))

    Get("/") ~> route ~> check {
      responseAs[String] === "testabc"
    }
  }
  "mapHttpResponseHeaders" in {
    // adds all request headers to the response
    val echoRequestHeaders = extract(_.request.headers).flatMap(respondWithHeaders)

    val removeIdHeader = mapHttpResponseHeaders(_.filterNot(_.lowercaseName == "id"))
    val route =
      removeIdHeader {
        echoRequestHeaders {
          complete("test")
        }
      }

    Get("/") ~> RawHeader("id", "12345") ~> RawHeader("id2", "67890") ~> route ~> check {
      header("id") === None
      header("id2").get.value === "67890"
    }
  }
  "mapHttpResponsePart" in {
    val prefixChunks = mapHttpResponsePart {
      case MessageChunk(data, _) => MessageChunk(HttpData("prefix"+data.asString))
      case x => x
    }
    val route =
      prefixChunks { ctx =>
        val resp = ctx.responder
        resp ! ChunkedResponseStart(HttpResponse())
        resp ! MessageChunk(HttpData("abc"))
        resp ! MessageChunk(HttpData("def"))
        resp ! ChunkedMessageEnd
      }

    Get("/") ~> route ~> check {
      chunks ===
        List(MessageChunk(HttpData("prefixabc")),
             MessageChunk(HttpData("prefixdef")))
    }
  }
  "mapInnerRoute" in {
    val completeWithInnerException =
      mapInnerRoute { route => ctx =>
        try {
          route(ctx)
        } catch {
          case NonFatal(e) => ctx.complete(s"Got ${e.getClass.getSimpleName} '${e.getMessage}'")
        }
      }

    val route =
      completeWithInnerException {
        complete(throw new IllegalArgumentException("BLIP! BLOP! Everything broke"))
      }

    Get("/") ~> route ~> check {
      responseAs[String] === "Got IllegalArgumentException 'BLIP! BLOP! Everything broke'"
    }
  }
  "mapRejections" in {
    // ignore any rejections and replace them by AuthorizationFailedRejection
    val replaceByAuthorizationFailed = mapRejections(_ => List(AuthorizationFailedRejection))
    val route =
      replaceByAuthorizationFailed {
        path("abc")(complete("abc"))
      }

    Get("/") ~> route ~> check {
      rejection === AuthorizationFailedRejection
    }
  }
  "0mapRequest" in {
    def transformToPostRequest(req: HttpRequest): HttpRequest = req.copy(method = HttpMethods.POST)
    val route =
      mapRequest(transformToPostRequest) {
        requestInstance { req =>
          complete(s"The request method was ${req.method}")
        }
      }

    Get("/") ~> route ~> check {
      responseAs[String] === "The request method was POST"
    }
  }
  implicit val timeout = RouteTestTimeout(DurationInt(10).millis dilated)
  "mapRequestContext" in {
    val probe = TestProbe()
    val replaceResponder = mapRequestContext(_.copy(responder = probe.ref))

    val route =
      replaceResponder {
        complete("abc")
      }

    Get("/abc/def/ghi") ~> route ~> check {
      handled === false
    }
    probe.expectMsgType[HttpMessagePartWrapper].messagePart === HttpResponse(entity = HttpEntity("abc"))
  }
  "0mapRouteResponse" in {
    val rejectAll = // not particularly useful directive
      mapRouteResponse {
        case _ => Rejected(List(AuthorizationFailedRejection))
      }
    val route =
      rejectAll {
        complete("abc")
      }

    Get("/") ~> route ~> check {
      rejections.nonEmpty === true
    }
  }
  "mapRouteResponsePF" in {
    case object MyCustomRejection extends Rejection
    val rejectRejections = // not particularly useful directive
      mapRouteResponsePF {
        case Rejected(_) => Rejected(List(AuthorizationFailedRejection))
      }
    val route =
      rejectRejections {
        reject(MyCustomRejection)
      }

    Get("/") ~> route ~> check {
      rejection === AuthorizationFailedRejection
    }
  }
  "noop" in {
    Get("/") ~> noop(complete("abc")) ~> check {
      responseAs[String] === "abc"
    }
  }
  "0provide" in {
    def providePrefixedString(value: String): Directive1[String] = provide("prefix:"+value)
    val route =
      providePrefixedString("test") { value =>
        complete(value)
      }
    Get("/") ~> route ~> check {
      responseAs[String] === "prefix:test"
    }
  }
  "routeRouteResponse" in {
    val completeWithRejectionNames =
      routeRouteResponse {
        case Rejected(rejs) => complete(s"Got these rejections: ${rejs.map(_.getClass.getSimpleName).mkString(", ")}")
      }

    val route = completeWithRejectionNames {
      reject(AuthorizationFailedRejection) ~
      post(complete("post"))
    }
    Get("/") ~> route ~> check {
      responseAs[String] === "Got these rejections: AuthorizationFailedRejection$, MethodRejection"
    }
  }
}
