package docs.directives

import spray.routing.UnacceptedResponseContentTypeRejection
import spray.http._
import HttpHeaders._

class RespondWithDirectivesExamplesSpec extends DirectivesSpec {

  "respondWithHeader-examples" in {
    val route =
      path("foo") {
        respondWithHeader(RawHeader("Funky-Muppet", "gonzo")) {
          complete("beep")
        }
      }

    Get("/foo") ~> route ~> check {
      header("Funky-Muppet") === Some(RawHeader("Funky-Muppet", "gonzo"))
      responseAs[String] === "beep"
    }
  }

  "respondWithHeaders-examples" in {
    val route =
      path("foo") {
        respondWithHeaders(RawHeader("Funky-Muppet", "gonzo"), Origin(Seq(HttpOrigin("http://spray.io")))) {
          complete("beep")
        }
      }

    Get("/foo") ~> route ~> check {
      header("Funky-Muppet") === Some(RawHeader("Funky-Muppet", "gonzo"))
      header[Origin] === Some(Origin(Seq(HttpOrigin("http://spray.io"))))
      responseAs[String] === "beep"
    }
  }

  "respondWithMediaType-examples" in {
    import MediaTypes._

    val route =
      path("foo") {
        respondWithMediaType(`application/json`) {
          complete("[]") // marshalled to `text/plain` here
        }
      }

    Get("/foo") ~> route ~> check {
      mediaType === `application/json`
      responseAs[String] === "[]"
    }

    Get("/foo") ~> Accept(MediaRanges.`text/*`) ~> route ~> check {
      rejection === UnacceptedResponseContentTypeRejection(ContentType(`application/json`) :: Nil)
    }
  }

  "respondWithSingletonHeader-examples" in {
    val respondWithMuppetHeader =
      respondWithSingletonHeader(RawHeader("Funky-Muppet", "gonzo"))

    val route =
      path("foo") {
        respondWithMuppetHeader {
          complete("beep")
        }
      } ~
      path("bar") {
        respondWithMuppetHeader {
          respondWithHeader(RawHeader("Funky-Muppet", "kermit")) {
            complete("beep")
          }
        }
      }

    Get("/foo") ~> route ~> check {
      headers.filter(_.is("funky-muppet")) === List(RawHeader("Funky-Muppet", "gonzo"))
      responseAs[String] === "beep"
    }

    Get("/bar") ~> route ~> check {
      headers.filter(_.is("funky-muppet")) === List(RawHeader("Funky-Muppet", "kermit"))
      responseAs[String] === "beep"
    }
  }

  "respondWithStatus-examples" in {
    val route =
      path("foo") {
        respondWithStatus(201) {
          complete("beep")
        }
      }

    Get("/foo") ~> route ~> check {
      status === StatusCodes.Created
      responseAs[String] === "beep"
    }
  }
}
