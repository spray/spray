/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package directives

import http._
import HttpHeaders._
import MediaTypes._
import HttpCharsets._
import test.AbstractSprayTest
import utils._
import encoding._

class CodecDirectivesSpec extends AbstractSprayTest {

  val echoRequestContent: Route = { ctx => ctx.complete(ctx.request.content.as[String].right.get) }
  val yeah: Route = _.complete("Yeah!")

  def haveContentEncoding(encoding: HttpEncoding) =
      beEqualTo(Some(`Content-Encoding`(encoding))) ^^ { (_: HttpResponse).headers.findByType[`Content-Encoding`] }

  def readAs(string: String, charset: String = "UTF8") = beEqualTo(string) ^^ { new String(_: Array[Byte], charset) }
  
  "the NoEncoding decoder" should {
    "decode the request content if it has encoding 'identidy'" in {
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.identity)), content = Some(HttpContent(`text/plain`, "yes")))) { 
        decodeRequest(NoEncoding) { echoRequestContent }
      }.response.content.as[String] mustEqual Right("yes")
    }
    "reject requests with content encoded with 'deflate'" in {
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.deflate)), content = Some(HttpContent(`text/plain`, "yes")))) { 
        decodeRequest(NoEncoding) { completeOk }
      }.rejections mustEqual Set(UnsupportedRequestEncodingRejection(HttpEncodings.identity))
    }
    "decode the request content if no Content-Encoding header is present" in {
      test(HttpRequest(content = Some(HttpContent(`text/plain`, "yes")))) { 
        decodeRequest(NoEncoding) { echoRequestContent }
      }.response.content.as[String] mustEqual Right("yes")
    }
    "leave request without content unchanged" in {
      test(HttpRequest()) { 
        decodeRequest(Gzip) { completeOk }
      }.response mustEqual Ok
    }
  }
  
  "the Gzip decoder" should {
    "decode the request content if it has encoding 'gzip'" in {
      val helloGzipped = fromHex("1f 8b 08 00 5e dc a2 4d 00 03 f3 48 cd c9 c9 07 00 82 89 d1 f7 05 00 00 00")
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.gzip)),
        content = Some(HttpContent(`text/plain`, helloGzipped)))) { 
        decodeRequest(Gzip) { echoRequestContent }
      }.response.content.as[String] mustEqual Right("Hello")
    }
    "reject the request content if it has encoding 'gzip' but is corrupt" in {
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.gzip)),
        content = Some(HttpContent(`text/plain`, fromHex("00 01 02"))))) { 
        decodeRequest(Gzip) { completeOk }
      }.rejections mustEqual Set(CorruptRequestEncodingRejection("Not in GZIP format"))
    }
    "reject requests with content encoded with 'deflate'" in {
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.deflate)),
        content = Some(HttpContent(`text/plain`, "Hello")))) { 
        decodeRequest(Gzip) { completeOk }
      }.rejections mustEqual Set(UnsupportedRequestEncodingRejection(HttpEncodings.gzip))
    }
    "reject requests without Content-Encoding header" in {
      test(HttpRequest(content = Some(HttpContent(`text/plain`, "Hello")))) { 
        decodeRequest(Gzip) { completeOk }
      }.rejections mustEqual Set(UnsupportedRequestEncodingRejection(HttpEncodings.gzip))
    }
    "leave request without content unchanged" in {
      test(HttpRequest()) { 
        decodeRequest(Gzip) { completeOk }
      }.response mustEqual Ok
    }
  }
  
  "the Gzip encoder" should {
    val yeahGzipped = fromHex("1f 8b 08 00 00 00 00 00 00 00 8b 4c 4d cc 50 04 00 70 0d 81 57 05 00 00 00")

    "encode the response content with GZIP if the client accepts it with a dedicated Accept-Encoding header" in {
      val response = test(HttpRequest(headers = List(`Accept-Encoding`(HttpEncodings.gzip)))) {
        encodeResponse(Gzip) { yeah }
      }.response
      response must haveContentEncoding(HttpEncodings.gzip)
      response.content mustEqual Some(HttpContent(ContentType(`text/plain`, `ISO-8859-1`), yeahGzipped))
    }
    "encode the response content with GZIP if the request has no Accept-Encoding header" in {
      test(HttpRequest()) { 
        encodeResponse(Gzip) { yeah }
      }.response.content mustEqual Some(HttpContent(ContentType(`text/plain`, `ISO-8859-1`), yeahGzipped))
    }
    "reject the request if the client does not accept GZIP encoding" in {
      test(HttpRequest(headers = List(`Accept-Encoding`(HttpEncodings.identity)))) { 
        encodeResponse(Gzip) { completeOk }
      }.rejections mustEqual Set(UnacceptedResponseEncodingRejection(HttpEncodings.gzip))
    }
    "leave responses without content unchanged" in {
      test(HttpRequest(headers = List(`Accept-Encoding`(HttpEncodings.gzip)))) { 
        encodeResponse(Gzip) { completeOk }
      }.response mustEqual Ok
    }
    "leave responses with an already set Content-Encoding header unchanged" in {
      test(HttpRequest(headers = List(`Accept-Encoding`(HttpEncodings.gzip)))) { 
        encodeResponse(Gzip) {
          respondWithHeader(`Content-Encoding`(HttpEncodings.identity)) { yeah }
        }
      }.response.content.as[String] mustEqual Right("Yeah!")
    }
    "correctly encode the chunk stream produced by a chunked response" in {
      val text = "This is a somewhat lengthy text that is being chunked by the autochunk directive!"
      val result = test(HttpRequest(headers = List(`Accept-Encoding`(HttpEncodings.gzip)))) {
        encodeResponse(Gzip) {
          autoChunk(8) {
            _.complete(text)
          }
        }
      }
      result.response must haveContentEncoding(HttpEncodings.gzip)
      Gzip.newDecompressor.decompress(result.chunks.toArray.flatMap(_.body)) must readAs(text)
    }
  }

  def fromHex(s: String) = s.split(' ').map(Integer.parseInt(_, 16).toByte)
}