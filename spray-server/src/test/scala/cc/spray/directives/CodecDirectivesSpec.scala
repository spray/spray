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
  val yeah: Route = completeWith("Yeah!")

  def haveContentEncoding(encoding: HttpEncoding) =
      beEqualTo(Some(`Content-Encoding`(encoding))) ^^ { (_: HttpResponse).headers.findByType[`Content-Encoding`] }

  def readAs(string: String, charset: String = "UTF8") = beEqualTo(string) ^^ { new String(_: Array[Byte], charset) }
  def hexDump(bytes: Array[Byte]) = bytes.map("%02x".format(_)).mkString
  def fromHexDump(dump: String) = dump.grouped(2).toArray.map(chars => Integer.parseInt(new String(chars), 16).toByte)
  
  "the NoEncoding decoder" should {
    "decode the request content if it has encoding 'identidy'" in {
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.identity)), content = Some(HttpContent(`text/plain`, "yes")))) { 
        decodeRequest(NoEncoding) { echoRequestContent }
      }.response.content.as[String] mustEqual Right("yes")
    }
    "reject requests with content encoded with 'deflate'" in {
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.deflate)), content = Some(HttpContent(`text/plain`, "yes")))) { 
        decodeRequest(NoEncoding) { completeWith(Ok) }
      }.rejections mustEqual Set(UnsupportedRequestEncodingRejection(HttpEncodings.identity))
    }
    "decode the request content if no Content-Encoding header is present" in {
      test(HttpRequest(content = Some(HttpContent(`text/plain`, "yes")))) { 
        decodeRequest(NoEncoding) { echoRequestContent }
      }.response.content.as[String] mustEqual Right("yes")
    }
    "leave request without content unchanged" in {
      test(HttpRequest()) { 
        decodeRequest(Gzip) { completeWith(Ok) }
      }.response mustEqual Ok
    }
  }
  
  "the Gzip decoder" should {
    "decode the request content if it has encoding 'gzip'" in {
      val helloGzipped = fromHexDump("1f8b08005edca24d0003f348cdc9c907008289d1f705000000")
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.gzip)),
        content = Some(HttpContent(`text/plain`, helloGzipped)))) { 
        decodeRequest(Gzip) { echoRequestContent }
      }.response.content.as[String] mustEqual Right("Hello")
    }
    "reject the request content if it has encoding 'gzip' but is corrupt" in {
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.gzip)),
        content = Some(HttpContent(`text/plain`, fromHexDump("000102"))))) {
        decodeRequest(Gzip) { completeWith(Ok) }
      }.rejections mustEqual Set(CorruptRequestEncodingRejection("Not in GZIP format"))
    }
    "reject requests with content encoded with 'deflate'" in {
      test(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.deflate)),
        content = Some(HttpContent(`text/plain`, "Hello")))) { 
        decodeRequest(Gzip) { completeWith(Ok) }
      }.rejections mustEqual Set(UnsupportedRequestEncodingRejection(HttpEncodings.gzip))
    }
    "reject requests without Content-Encoding header" in {
      test(HttpRequest(content = Some(HttpContent(`text/plain`, "Hello")))) { 
        decodeRequest(Gzip) { completeWith(Ok) }
      }.rejections mustEqual Set(UnsupportedRequestEncodingRejection(HttpEncodings.gzip))
    }
    "leave request without content unchanged" in {
      test(HttpRequest()) { 
        decodeRequest(Gzip) { completeWith(Ok) }
      }.response mustEqual Ok
    }
  }
  
  "the Gzip encoder" should {
    val yeahGzipped = fromHexDump("1f8b08000000000000008b4c4dcc500400700d815705000000")

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
        encodeResponse(Gzip) { completeWith(Ok) }
      }.rejections mustEqual Set(UnacceptedResponseEncodingRejection(HttpEncodings.gzip))
    }
    "leave responses without content unchanged" in {
      test(HttpRequest(headers = List(`Accept-Encoding`(HttpEncodings.gzip)))) { 
        encodeResponse(Gzip) { completeWith(Ok) }
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
            completeWith(text)
          }
        }
      }
      result.response must haveContentEncoding(HttpEncodings.gzip)
      val bytes = result.response.content.get.buffer ++ result.chunks.toArray.flatMap(_.body)
      Gzip.newDecompressor.decompress(bytes) must readAs(text)
    }
  }

}