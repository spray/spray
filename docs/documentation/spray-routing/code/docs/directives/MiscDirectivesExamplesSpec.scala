package docs.directives

import spray.http.HttpHeaders.`Remote-Address`
import spray.routing.Rejection
import spray.http._
import spray.util._
import spray.http.Uri.Path
import spray.routing.MethodRejection
import spray.routing.ValidationRejection

class MiscDirectivesExamplesSpec extends DirectivesSpec {
  "cancelAllRejections-example" in {
    def isMethodRejection: Rejection => Boolean = {
      case MethodRejection(_) => true
      case _ => false
    }

    val route =
      cancelAllRejections(isMethodRejection) {
        post {
          complete("Result")
        }
      }

    Get("/") ~> route ~> check {
      rejections === Nil
      handled === false
    }
  }
  "cancelRejection-example" in {
    val route =
      cancelRejection(MethodRejection(HttpMethods.POST)) {
        post {
          complete("Result")
        }
      }

    Get("/") ~> route ~> check {
      rejections === Nil
      handled === false
    }
  }
  "clientIP-example" in {
    val route = clientIP { ip =>
      complete("Client's ip is " + ip.toOption.map(_.getHostAddress).getOrElse("unknown"))
    }

    Get("/").withHeaders(`Remote-Address`("192.168.3.12")) ~> route ~> check {
      responseAs[String] === "Client's ip is 192.168.3.12"
    }
  }
  "jsonpWithParameter-example" in {
    case class Test(abc: Int)
    object TestProtocol {
      import spray.json.DefaultJsonProtocol._
      implicit val testFormat = jsonFormat(Test, "abc")
    }
    val route =
      jsonpWithParameter("jsonp") {
        import TestProtocol._
        import spray.httpx.SprayJsonSupport._
        complete(Test(456))
      }

    Get("/?jsonp=result") ~> route ~> check {
      responseAs[String] ===
        """result({
          |  "abc": 456
          |})""".stripMarginWithNewline("\n")
      contentType === MediaTypes.`application/javascript`.withCharset(HttpCharsets.`UTF-8`)
    }
    Get("/") ~> route ~> check {
      responseAs[String] ===
        """{
          |  "abc": 456
          |}""".stripMarginWithNewline("\n")
      contentType === ContentTypes.`application/json`
    }
  }
  "rejectEmptyResponse-example" in {
    val route = rejectEmptyResponse {
      path("even" / IntNumber) { i =>
        complete {
          // returns Some(evenNumberDescription) or None
          Option(i).filter(_ % 2 == 0).map { num =>
            s"Number $num is even."
          }
        }
      }
    }

    Get("/even/23") ~> sealRoute(route) ~> check {
      status === StatusCodes.NotFound
    }
    Get("/even/28") ~> route ~> check {
      responseAs[String] === "Number 28 is even."
    }
  }
  "requestEntityEmptyPresent-example" in {
    val route =
      requestEntityEmpty {
        complete("request entity empty")
      } ~
      requestEntityPresent {
        complete("request entity present")
      }

    Post("/", "text") ~> sealRoute(route) ~> check {
      responseAs[String] === "request entity present"
    }
    Post("/") ~> route ~> check {
      responseAs[String] === "request entity empty"
    }
  }
  "requestInstance-example" in {
    val route =
      requestInstance { request =>
        complete(s"Request method is ${request.method} and length is ${request.entity.data.length}")
      }

    Post("/", "text") ~> route ~> check {
      responseAs[String] === "Request method is POST and length is 4"
    }
    Get("/") ~> route ~> check {
      responseAs[String] === "Request method is GET and length is 0"
    }
  }
  "requestUri-example" in {
    val route =
      requestUri { uri =>
        complete(s"Full URI: $uri")
      }

    Get("/") ~> route ~> check {
      // tests are executed with the host assumed to be "example.com"
      responseAs[String] === "Full URI: http://example.com/"
    }
    Get("/test") ~> route ~> check {
      responseAs[String] === "Full URI: http://example.com/test"
    }
  }
  "rewriteUnmatchedPath-example" in {
    def ignore456(path: Path) = path match {
      case s@Path.Segment(head, tail) if head.startsWith("456") =>
        val newHead = head.drop(3)
        if (newHead.isEmpty) tail
        else s.copy(head = head.drop(3))
      case _ => path
    }
    val ignoring456 = rewriteUnmatchedPath(ignore456)

    val route =
      pathPrefix("123") {
        ignoring456 {
          path("abc") {
            complete(s"Content")
          }
        }
      }

    Get("/123/abc") ~> route ~> check {
      responseAs[String] === "Content"
    }
    Get("/123456/abc") ~> route ~> check {
      responseAs[String] === "Content"
    }
  }
  "unmatchedPath-example" in {
    val route =
      pathPrefix("abc") {
        unmatchedPath { remaining =>
          complete(s"Unmatched: '$remaining'")
        }
      }

    Get("/abc") ~> route ~> check {
      responseAs[String] === "Unmatched: ''"
    }
    Get("/abc/456") ~> route ~> check {
      responseAs[String] === "Unmatched: '/456'"
    }
  }
  "validate-example" in {
    val route =
      requestUri { uri =>
        validate(uri.path.toString.size < 5, s"Path too long: '${uri.path.toString}'") {
          complete(s"Full URI: $uri")
        }
      }

    Get("/234") ~> route ~> check {
      responseAs[String] === "Full URI: http://example.com/234"
    }
    Get("/abcdefghijkl") ~> route ~> check {
      rejection === ValidationRejection("Path too long: '/abcdefghijkl'", None)
    }
  }
}
