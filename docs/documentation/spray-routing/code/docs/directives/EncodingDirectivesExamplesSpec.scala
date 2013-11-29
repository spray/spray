package docs.directives

import spray.http.HttpHeaders.{`Content-Encoding`, `Accept-Encoding`}
import spray.http.HttpEncodings._
import spray.http.{StatusCodes, HttpResponse, HttpEncoding}
import spray.httpx.encoding.{NoEncoding, Deflate, Encoder, Gzip}
import spray.routing.{UnsupportedRequestEncodingRejection, UnacceptedResponseEncodingRejection}

class EncodingDirectivesExamplesSpec extends DirectivesSpec {
  "compressResponse-0" in {
    val route = compressResponse() { complete("content") }

    Get("/") ~> route ~> check {
      response must haveContentEncoding(gzip)
    }
    Get("/") ~> `Accept-Encoding`(gzip, deflate) ~> route ~> check {
      response must haveContentEncoding(gzip)
    }
    Get("/") ~> `Accept-Encoding`(deflate) ~> route ~> check {
      response must haveContentEncoding(deflate)
    }
    Get("/") ~> `Accept-Encoding`(identity) ~> route ~> check {
      status === StatusCodes.OK
      response must haveContentEncoding(identity)
      responseAs[String] === "content"
    }
  }
  "compressResponse-1" in {
    val route = compressResponse(Gzip) { complete("content") }

    Get("/") ~> route ~> check {
      response must haveContentEncoding(gzip)
    }
    Get("/") ~> `Accept-Encoding`(gzip, deflate) ~> route ~> check {
      response must haveContentEncoding(gzip)
    }
    Get("/") ~> `Accept-Encoding`(deflate) ~> route ~> check {
      rejection === UnacceptedResponseEncodingRejection(gzip)
    }
    Get("/") ~> `Accept-Encoding`(identity) ~> route ~> check {
      rejection === UnacceptedResponseEncodingRejection(gzip)
    }
  }
  "compressResponseIfRequested" in {
    val route = compressResponseIfRequested() { complete("content") }

    Get("/") ~> route ~> check {
      response must haveContentEncoding(identity)
    }
    Get("/") ~> `Accept-Encoding`(gzip, deflate) ~> route ~> check {
      response must haveContentEncoding(gzip)
    }
    Get("/") ~> `Accept-Encoding`(deflate) ~> route ~> check {
      response must haveContentEncoding(deflate)
    }
    Get("/") ~> `Accept-Encoding`(identity) ~> route ~> check {
      response must haveContentEncoding(identity)
    }
  }
  "encodeResponse" in {
    val route = encodeResponse(Gzip) { complete("content") }

    Get("/") ~> route ~> check {
      response must haveContentEncoding(gzip)
    }
    Get("/") ~> `Accept-Encoding`(gzip, deflate) ~> route ~> check {
      response must haveContentEncoding(gzip)
    }
    Get("/") ~> `Accept-Encoding`(deflate) ~> route ~> check {
      rejection === UnacceptedResponseEncodingRejection(gzip)
    }
    Get("/") ~> `Accept-Encoding`(identity) ~> route ~> check {
      rejection === UnacceptedResponseEncodingRejection(gzip)
    }
  }

  val helloGzipped = compress("Hello", Gzip)
  val helloDeflated = compress("Hello", Deflate)
  "decodeRequest" in {
    val route =
      decodeRequest(Gzip) {
        entity(as[String]) { content: String =>
          complete(s"Request content: '$content'")
        }
      }

    Get("/", helloGzipped) ~> `Content-Encoding`(gzip) ~> route ~> check {
      responseAs[String] === "Request content: 'Hello'"
    }
    Get("/", helloDeflated) ~> `Content-Encoding`(deflate) ~> route ~> check {
      rejection === UnsupportedRequestEncodingRejection(gzip)
    }
    Get("/", "hello") ~> `Content-Encoding`(identity) ~> route ~> check {
      rejection === UnsupportedRequestEncodingRejection(gzip)
    }
  }
  "decompressRequest-0" in {
    val route =
      decompressRequest() {
        entity(as[String]) { content: String =>
          complete(s"Request content: '$content'")
        }
      }

    Get("/", helloGzipped) ~> `Content-Encoding`(gzip) ~> route ~> check {
      responseAs[String] === "Request content: 'Hello'"
    }
    Get("/", helloDeflated) ~> `Content-Encoding`(deflate) ~> route ~> check {
      responseAs[String] === "Request content: 'Hello'"
    }
    Get("/", "hello uncompressed") ~> `Content-Encoding`(identity) ~> route ~> check {
      responseAs[String] === "Request content: 'hello uncompressed'"
    }
  }
  "decompressRequest-1" in {
    val route =
      decompressRequest(Gzip, NoEncoding) {
        entity(as[String]) { content: String =>
          complete(s"Request content: '$content'")
        }
      }

    Get("/", helloGzipped) ~> `Content-Encoding`(gzip) ~> route ~> check {
      responseAs[String] === "Request content: 'Hello'"
    }
    Get("/", helloDeflated) ~> `Content-Encoding`(deflate) ~> route ~> check {
      rejections === List(UnsupportedRequestEncodingRejection(gzip), UnsupportedRequestEncodingRejection(identity))
    }
    Get("/", "hello uncompressed") ~> `Content-Encoding`(identity) ~> route ~> check {
      responseAs[String] === "Request content: 'hello uncompressed'"
    }
  }

  def haveContentEncoding(encoding: HttpEncoding) =
    beEqualTo(encoding) ^^ { (_: HttpResponse).encoding }

  def compress(input: String, encoder: Encoder) = encoder.newCompressor.compress(input.getBytes).finish
}
