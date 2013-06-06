/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.can.rendering

import org.specs2.matcher.DataTables
import org.specs2.specification.Scope
import org.specs2._
import akka.spray.NoLogging
import spray.util._
import spray.http._
import HttpHeaders._
import HttpMethods._
import HttpProtocols._

class ResponseRendererSpec extends mutable.Specification with DataTables {

  "The response preparation logic" should {
    "properly render" in {

      "a response with status 200, no headers and no body" in new TestSetup() {
        render(HttpResponse(200)) === result {
          """HTTP/1.1 200 OK
            |Server: spray-can/1.0.0
            |Date: Thu, 25 Aug 2011 09:10:29 GMT
            |Content-Length: 0
            |
            |"""
        } -> false
      }

      "a response with status 304, a few headers and no body" in new TestSetup() {
        render {
          HttpResponse(304, headers = List(RawHeader("X-Fancy", "of course"), RawHeader("Age", "0")))
        } === result {
          """HTTP/1.1 304 Not Modified
            |Server: spray-can/1.0.0
            |Date: Thu, 25 Aug 2011 09:10:29 GMT
            |X-Fancy: of course
            |Age: 0
            |Content-Length: 0
            |
            |"""
        } -> false
      }

      "a response with status 400, a few headers and a body" in new TestSetup() {
        render {
          HttpResponse(
            status = 400,
            headers = List(RawHeader("Age", "30"), Connection("Keep-Alive")),
            entity = "Small f*ck up overhere!")
        } === result {
          """HTTP/1.1 400 Bad Request
            |Server: spray-can/1.0.0
            |Date: Thu, 25 Aug 2011 09:10:29 GMT
            |Content-Type: text/plain
            |Age: 30
            |Connection: Keep-Alive
            |Content-Length: 23
            |
            |Small f*ck up overhere!"""
        } -> false
      }

      "a response to a HEAD request" in new TestSetup() {
        render(requestMethod = HEAD,
          response = HttpResponse(
            headers = List(RawHeader("Age", "30"), Connection("Keep-Alive")),
            entity = "Small f*ck up overhere!")) === result {
            """HTTP/1.1 200 OK
            |Server: spray-can/1.0.0
            |Date: Thu, 25 Aug 2011 09:10:29 GMT
            |Content-Type: text/plain
            |Age: 30
            |Connection: Keep-Alive
            |Content-Length: 23
            |
            |"""
          } -> false
      }

      "a chunked response without body" in new TestSetup() {
        render(
          response = ChunkedResponseStart(HttpResponse(200, headers = List(RawHeader("Age", "30")))),
          requestConnectionHeader = Some("close")) === result {
            """HTTP/1.1 200 OK
            |Server: spray-can/1.0.0
            |Date: Thu, 25 Aug 2011 09:10:29 GMT
            |Age: 30
            |Transfer-Encoding: chunked
            |
            |"""
          } -> false
      }

      "a chunked response with body" in new TestSetup() {
        render(ChunkedResponseStart(HttpResponse(entity = "Yahoooo"))) === result {
          """HTTP/1.1 200 OK
            |Server: spray-can/1.0.0
            |Date: Thu, 25 Aug 2011 09:10:29 GMT
            |Content-Type: text/plain
            |Transfer-Encoding: chunked
            |
            |7
            |Yahoooo
            |"""
        } -> false
      }

      "a response chunk" in new TestSetup() {
        render(MessageChunk("body123".getBytes("ISO-8859-1"), """key=value;another="tl;dr"""")) === result {
          """7;key=value;another="tl;dr"
            |body123
            |"""
        } -> false
      }

      "a final response chunk" in new TestSetup() {
        render(ChunkedMessageEnd("", List(RawHeader("Age", "30"), RawHeader("Cache-Control", "public")))) === result {
          """0
            |Age: 30
            |Cache-Control: public
            |
            |"""
        } -> false
      }

      "a chunkless chunked response without body" in new TestSetup(chunklessStreaming = true) {
        render(response = ChunkedResponseStart(HttpResponse(200, headers = List(RawHeader("Age", "30"))))) === result {
          """HTTP/1.1 200 OK
            |Server: spray-can/1.0.0
            |Date: Thu, 25 Aug 2011 09:10:29 GMT
            |Age: 30
            |
            |"""
        } -> false
      }

      "a chunkless chunked response with body" in new TestSetup(chunklessStreaming = true) {
        render(response = ChunkedResponseStart(HttpResponse(entity = "Yahoooo"))) === result {
          """HTTP/1.1 200 OK
            |Server: spray-can/1.0.0
            |Date: Thu, 25 Aug 2011 09:10:29 GMT
            |Content-Type: text/plain
            |
            |Yahoooo"""
        } -> false
      }

      "a chunkless response chunk" in new TestSetup(chunklessStreaming = true) {
        render(response = MessageChunk("body123".getBytes("ISO-8859-1"), """key=value;another="tl;dr"""")) === result {
          "body123"
        } -> false
      }

      "a chunkless final response chunk" in new TestSetup(chunklessStreaming = true) {
        render(response = ChunkedMessageEnd("",
          List(RawHeader("Age", "30"), RawHeader("Cache-Control", "public")))) === result("") -> true
      }

      "The 'Connection' header should be rendered correctly" in new TestSetup() {
        val NONE: Option[String] = None

        "Client Version" | "Request" | "Response" | "Rendered" | "Close" |
          `HTTP/1.1` ! NONE ! NONE ! NONE ! false |
          `HTTP/1.1` ! Some("close") ! NONE ! Some("close") ! true |
          `HTTP/1.1` ! Some("Keep-Alive") ! NONE ! NONE ! false |
          `HTTP/1.0` ! NONE ! NONE ! NONE ! true |
          `HTTP/1.0` ! Some("close") ! NONE ! NONE ! true |
          `HTTP/1.0` ! Some("Keep-Alive") ! NONE ! Some("Keep-Alive") ! false |
          `HTTP/1.1` ! NONE ! Some("close") ! Some("close") ! true |
          `HTTP/1.0` ! Some("close") ! Some("Keep-Alive") ! Some("Keep-Alive") ! false |> {

            (reqProto, reqCH, resCH, renCH, close) ⇒
              render(
                response = HttpResponse(200, headers = resCH.map(h ⇒ List(HttpHeaders.Connection(h))) getOrElse Nil),
                requestProtocol = reqProto,
                requestConnectionHeader = reqCH) === result {
                  "HTTP/1.1 200 OK\n" +
                    "Server: spray-can/1.0.0\n" +
                    "Date: Thu, 25 Aug 2011 09:10:29 GMT\n" +
                    renCH.map("Connection: " + _ + "\n").getOrElse("") +
                    "Content-Length: 0\n\n"
                } -> close
          }
      }
    }
  }

  class TestSetup(val serverHeaderValue: String = "spray-can/1.0.0", val chunklessStreaming: Boolean = false)
      extends ResponseRenderingComponent with Scope {

    def render(response: HttpResponsePart,
               requestMethod: HttpMethod = HttpMethods.GET,
               requestProtocol: HttpProtocol = `HTTP/1.1`,
               requestConnectionHeader: Option[String] = None) = {
      val connectionHeader = requestConnectionHeader map (Connection(_))
      val closeAfterResponseCompletion = requestProtocol match {
        case `HTTP/1.1` ⇒ connectionHeader.isDefined && connectionHeader.get.hasClose
        case `HTTP/1.0` ⇒ connectionHeader.isEmpty || !connectionHeader.get.hasKeepAlive
      }
      val rendering = new ByteStringRendering(256)
      val closeAfterWrite = renderResponsePartRenderingContext(rendering,
        ResponsePartRenderingContext(response, requestMethod, requestProtocol, closeAfterResponseCompletion),
        NoLogging)
      rendering.get.utf8String -> closeAfterWrite
    }

    def result(content: String) = content.stripMargin.replace(EOL, "\r\n")

    override def dateTime(now: Long) = DateTime(2011, 8, 25, 9, 10, 29) // provide a stable date for testing
  }
}
