/*
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.routing

import spray.util._
import spray.httpx.encoding._
import spray.http._
import HttpHeaders._
import HttpCharsets._
import HttpEncodings._
import MediaTypes._

class EncodingDirectivesSpec extends RoutingSpec {

  val echoRequestContent: Route = { ctx ⇒ ctx.complete(ctx.request.entity.asString) }
  val yeah = complete("Yeah!")

  "the NoEncoding decoder" should {
    "decode the request content if it has encoding 'identity'" in {
      Get("/", "yes") ~> addHeader(`Content-Encoding`(identity)) ~> {
        decodeRequest(NoEncoding) { echoRequestContent }
      } ~> check { entityAs[String] === "yes" }
    }
    "reject requests with content encoded with 'deflate'" in {
      Get("/", "yes") ~> addHeader(`Content-Encoding`(deflate)) ~> {
        decodeRequest(NoEncoding) { echoRequestContent }
      } ~> check { rejection === UnsupportedRequestEncodingRejection(identity) }
    }
    "decode the request content if no Content-Encoding header is present" in {
      Get("/", "yes") ~> decodeRequest(NoEncoding) { echoRequestContent } ~> check { entityAs[String] === "yes" }
    }
    "leave request without content unchanged" in {
      Get() ~> decodeRequest(Gzip) { completeOk } ~> check { response === Ok }
    }
  }

  "the Gzip decoder" should {
    "decode the request content if it has encoding 'gzip'" in {
      val helloGzipped = fromHexDump("1f8b08005edca24d0003f348cdc9c907008289d1f705000000")
      Get("/", helloGzipped) ~> addHeader(`Content-Encoding`(gzip)) ~> {
        decodeRequest(Gzip) { echoRequestContent }
      } ~> check { entityAs[String] === "Hello" }
    }
    "reject the request content if it has encoding 'gzip' but is corrupt" in {
      Get("/", fromHexDump("000102")) ~> addHeader(`Content-Encoding`(gzip)) ~> {
        decodeRequest(Gzip) { completeOk }
      } ~> check { rejection === CorruptRequestEncodingRejection("Not in GZIP format") }
    }
    "reject requests with content encoded with 'deflate'" in {
      Get("/", "Hello") ~> addHeader(`Content-Encoding`(deflate)) ~> {
        decodeRequest(Gzip) { completeOk }
      } ~> check { rejection === UnsupportedRequestEncodingRejection(gzip) }
    }
    "reject requests without Content-Encoding header" in {
      Get("/", "Hello") ~> {
        decodeRequest(Gzip) { completeOk }
      } ~> check { rejection === UnsupportedRequestEncodingRejection(gzip) }
    }
    "leave request without content unchanged" in {
      Get() ~> {
        decodeRequest(Gzip) { completeOk }
      } ~> check { response === Ok }
    }
  }

  "the Gzip encoder" should {
    val yeahGzipped = fromHexDump("1f8b08000000000000008b4c4dcc500400700d815705000000")

    "encode the response content with GZIP if the client accepts it with a dedicated Accept-Encoding header" in {
      Get() ~> addHeader(`Accept-Encoding`(gzip)) ~> {
        encodeResponse(Gzip) { yeah }
      } ~> check {
        response must haveContentEncoding(gzip)
        body === HttpBody(ContentType(`text/plain`, `UTF-8`), yeahGzipped)
      }
    }
    "encode the response content with GZIP if the request has no Accept-Encoding header" in {
      Get() ~> {
        encodeResponse(Gzip) { yeah }
      } ~> check { body === HttpBody(ContentType(`text/plain`, `UTF-8`), yeahGzipped) }
    }
    "reject the request if the client does not accept GZIP encoding" in {
      Get() ~> addHeader(`Accept-Encoding`(identity)) ~> {
        encodeResponse(Gzip) { completeOk }
      } ~> check { rejection === UnacceptedResponseEncodingRejection(gzip) }
    }
    "leave responses without content unchanged" in {
      Get() ~> addHeader(`Accept-Encoding`(gzip)) ~> {
        encodeResponse(Gzip) { completeOk }
      } ~> check { response === Ok }
    }
    "leave responses with an already set Content-Encoding header unchanged" in {
      Get() ~> addHeader(`Accept-Encoding`(gzip)) ~> {
        encodeResponse(Gzip) {
          respondWithHeader(`Content-Encoding`(identity)) { yeah }
        }
      } ~> check { entityAs[String] === "Yeah!" }
    }
    "correctly encode the chunk stream produced by a chunked response" in {
      val text = "This is a somewhat lengthy text that is being chunked by the autochunk directive!"
      Get() ~> addHeader(`Accept-Encoding`(gzip)) ~> {
        encodeResponse(Gzip) {
          autoChunk(8) {
            complete(text)
          }
        }
      } ~> check {
        response must haveContentEncoding(gzip)
        val bytes = body.buffer ++ chunks.toArray.flatMap(_.body)
        Gzip.newDecompressor.decompress(bytes) must readAs(text)
      }
    }
  }

  "the encodeResponse(NoEncoding) directive" should {
    "produce a response if no Accept-Encoding is present in the request" in {
      Get() ~> encodeResponse(NoEncoding) { completeOk } ~> check { response === Ok }
    }
    "produce a response if the client explicitly accepts non-encoded responses" in {
      Get() ~> addHeader(`Accept-Encoding`(gzip, identity)) ~> {
        encodeResponse(NoEncoding) { completeOk }
      } ~> check { response === Ok }
    }
    "reject the request if the client does not accept `identity` encoding" in {
      Get() ~> addHeader(`Accept-Encoding`(gzip)) ~> {
        encodeResponse(NoEncoding) { completeOk }
      } ~> check { rejection === UnacceptedResponseEncodingRejection(identity) }
    }
    "reject the request if the request has an 'Accept-Encoding: identity; q=0' header" in {
      pending
    }
  }

  "a (encodeResponse(Gzip) | encodeResponse(NoEncoding)) compound directive" should {
    val encodeGzipOrIdentity = (encodeResponse(Gzip) | encodeResponse(NoEncoding))
    "produce a GZIP encoded response if the request has no Accept-Encoding header" in {
      Get() ~> {
        encodeGzipOrIdentity { yeah }
      } ~> check { response must haveContentEncoding(gzip) }
    }
    "produce a GZIP encoded response if the request has an `Accept-Encoding: deflate, gzip` header" in {
      Get() ~> addHeader(`Accept-Encoding`(deflate, gzip)) ~> {
        encodeGzipOrIdentity { yeah }
      } ~> check { response must haveContentEncoding(gzip) }
    }
    "produce a non-encoded response if the request has an `Accept-Encoding: identity` header" in {
      Get() ~> addHeader(`Accept-Encoding`(identity)) ~> {
        encodeGzipOrIdentity { completeOk }
      } ~> check { response === Ok }
    }
    "reject the request if it has an `Accept-Encoding: deflate` header" in {
      Get() ~> addHeader(`Accept-Encoding`(deflate)) ~> {
        encodeGzipOrIdentity { completeOk }
      } ~> check {
        rejections === Seq(
          UnacceptedResponseEncodingRejection(gzip),
          UnacceptedResponseEncodingRejection(identity))
      }
    }
  }

  "a (encodeResponse(NoEncoding) | encodeResponse(Gzip)) compound directive" should {
    val encodeIdentityOrGzip = (encodeResponse(NoEncoding) | encodeResponse(Gzip))
    "produce a non-encoded encoded response if the request has no Accept-Encoding header" in {
      Get() ~> {
        encodeIdentityOrGzip { completeOk }
      } ~> check { response === Ok }
    }
    "produce a GZIP encoded response if the request has an `Accept-Encoding: deflate, gzip` header" in {
      Get() ~> addHeader(`Accept-Encoding`(deflate, gzip)) ~> {
        encodeIdentityOrGzip { yeah }
      } ~> check { response must haveContentEncoding(gzip) }
    }
    "produce a non-encoded response if the request has an `Accept-Encoding: identity` header" in {
      Get() ~> addHeader(`Accept-Encoding`(identity)) ~> {
        encodeIdentityOrGzip { completeOk }
      } ~> check { response === Ok }
    }
    "reject the request if it has an `Accept-Encoding: deflate` header" in {
      Get() ~> addHeader(`Accept-Encoding`(deflate)) ~> {
        encodeIdentityOrGzip { completeOk }
      } ~> check {
        rejections === Seq(
          UnacceptedResponseEncodingRejection(identity),
          UnacceptedResponseEncodingRejection(gzip))
      }
    }
  }

  def hexDump(bytes: Array[Byte]) = bytes.map("%02x" format _).mkString
  def fromHexDump(dump: String) = dump.grouped(2).toArray.map(chars ⇒ Integer.parseInt(new String(chars), 16).toByte)

  def haveContentEncoding(encoding: HttpEncoding) =
    beEqualTo(Some(`Content-Encoding`(encoding))) ^^ { (_: HttpResponse).headers.findByType[`Content-Encoding`] }

  def readAs(string: String, charset: String = "UTF8") = beEqualTo(string) ^^ { new String(_: Array[Byte], charset) }
}