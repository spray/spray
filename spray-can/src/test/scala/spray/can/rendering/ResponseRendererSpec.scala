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

package spray.can.rendering

import org.specs2.matcher.DataTables
import org.specs2._
import spray.util._
import spray.http._
import HttpHeaders.RawHeader
import HttpProtocols._


class ResponseRendererSpec extends mutable.Specification with DataTables {

  "The response preparation logic" should {
    "properly render" in {

      "a response with status 200, no headers and no body" in {
        Context(HttpResponse(200)) must beRenderedTo(
          content = """|HTTP/1.1 200 OK
                       |Server: spray-can/1.0.0
                       |Date: Thu, 25 Aug 2011 09:10:29 GMT
                       |Content-Length: 0
                       |
                       |""",
          close = false
        )
      }

      "a response with status 304, a few headers and no body" in {
        Context(
          HttpResponse(304, headers = List(
            RawHeader("X-Fancy", "of course"),
            RawHeader("Age", "0")
          ))
        ) must beRenderedTo(
          content = """|HTTP/1.1 304 Not Modified
                       |X-Fancy: of course
                       |Age: 0
                       |Server: spray-can/1.0.0
                       |Date: Thu, 25 Aug 2011 09:10:29 GMT
                       |Content-Length: 0
                       |
                       |""",
          close = false
        )
      }

      "a response with status 400, a few headers and a body" in {
        Context(
          HttpResponse(
            status = 400,
            headers = List(RawHeader("Age", "30"), RawHeader("Connection", "Keep-Alive")),
            entity = "Small f*ck up overhere!",
            protocol = `HTTP/1.0`
          )
        ) must beRenderedTo(
          content = """|HTTP/1.0 400 Bad Request
                       |Age: 30
                       |Connection: Keep-Alive
                       |Server: spray-can/1.0.0
                       |Date: Thu, 25 Aug 2011 09:10:29 GMT
                       |Content-Type: text/plain
                       |Content-Length: 23
                       |
                       |Small f*ck up overhere!""",
          close = false
        )
      }

      "a response to a HEAD request" in {
        Context(
          response = HttpResponse(
            headers = List(RawHeader("Age", "30"), RawHeader("Connection", "Keep-Alive")),
            entity = "Small f*ck up overhere!"
          ),
          requestMethod = HttpMethods.HEAD
        ) must beRenderedTo(
          content = """|HTTP/1.1 200 OK
                       |Age: 30
                       |Connection: Keep-Alive
                       |Server: spray-can/1.0.0
                       |Date: Thu, 25 Aug 2011 09:10:29 GMT
                       |Content-Type: text/plain
                       |Content-Length: 23
                       |
                       |""",
          close = false
        )
      }

      "a non-keepalive HTTP/1.0 message" in {
        Context(
          HttpResponse(
            status = 200,
            headers = List(RawHeader("Age", "30"), RawHeader("Cache-Control", "public")),
            protocol = `HTTP/1.0`,
            entity = "Small f*ck up overhere!"
          )
        ) must beRenderedTo(
          content = """|HTTP/1.0 200 OK
                       |Age: 30
                       |Cache-Control: public
                       |Server: spray-can/1.0.0
                       |Date: Thu, 25 Aug 2011 09:10:29 GMT
                       |Content-Type: text/plain
                       |
                       |Small f*ck up overhere!""",
          close = true
        )
      }

      "a chunked response without body" in {
        Context(
          response = ChunkedResponseStart(HttpResponse(200, headers = List(RawHeader("Age", "30")))),
          requestConnectionHeader = Some("close")
        ) must beRenderedTo(
          content = """|HTTP/1.1 200 OK
                       |Age: 30
                       |Transfer-Encoding: chunked
                       |Server: spray-can/1.0.0
                       |Date: Thu, 25 Aug 2011 09:10:29 GMT
                       |
                       |""",
          close = false
        )
      }

      "a chunked response with body" in {
        Context(ChunkedResponseStart(HttpResponse(entity = "Yahoooo"))) must beRenderedTo(
          content = """|HTTP/1.1 200 OK
                       |Transfer-Encoding: chunked
                       |Server: spray-can/1.0.0
                       |Date: Thu, 25 Aug 2011 09:10:29 GMT
                       |Content-Type: text/plain
                       |
                       |7
                       |Yahoooo
                       |""",
          close = false
        )
      }

      "a response chunk" in {
        Context(
          MessageChunk(
            "body123".getBytes("ISO-8859-1"),
            List(ChunkExtension("key", "value"), ChunkExtension("another", "tl;dr"))
          )
        ) must beRenderedTo(
          content = """|7;key=value;another="tl;dr"
                       |body123
                       |""",
          close = false
        )
      }

      "a final response chunk" in {
        Context(
          ChunkedMessageEnd(Nil, List(RawHeader("Age", "30"), RawHeader("Cache-Control", "public")))
        ) must beRenderedTo(
          content = """|0
                       |Age: 30
                       |Cache-Control: public
                       |
                       |""",
          close = false
        )
      }

      "a chunkless chunked response without body" in {
        Context(
          response = ChunkedResponseStart(HttpResponse(200, headers = List(RawHeader("Age", "30")))),
          chunkless = true
        ) must beRenderedTo(
          content = """|HTTP/1.1 200 OK
                       |Age: 30
                       |Server: spray-can/1.0.0
                       |Date: Thu, 25 Aug 2011 09:10:29 GMT
                       |
                       |""",
          close = false
        )
      }

      "a chunkless chunked response with body" in {
        Context(
          response = ChunkedResponseStart(HttpResponse(entity = "Yahoooo")),
          chunkless = true
        ) must beRenderedTo(
          content = """|HTTP/1.1 200 OK
                       |Server: spray-can/1.0.0
                       |Date: Thu, 25 Aug 2011 09:10:29 GMT
                       |Content-Type: text/plain
                       |
                       |Yahoooo""",
          close = false
        )
      }

      "a chunkless response chunk" in {
        Context(
          response = MessageChunk(
            "body123".getBytes("ISO-8859-1"),
            List(ChunkExtension("key", "value"), ChunkExtension("another", "tl;dr"))
          ),
          chunkless = true
        ) must beRenderedTo(
          content = "body123",
          close = false
        )
      }

      "a chunkless final response chunk" in {
        Context(
          response = ChunkedMessageEnd(Nil, List(RawHeader("Age", "30"), RawHeader("Cache-Control", "public"))),
          chunkless = true
        ) must beRenderedTo(
          content = "",
          close = true
        )
      }

      "The 'Connection' header should be rendered correctly" in {
        val NONE: Option[String] = None

        "Client Version" | "Request"          | "Response"         | "Rendered"         | "Close" |
        `HTTP/1.1`       ! NONE               ! NONE               ! NONE               ! false   |
        `HTTP/1.1`       ! Some("close")      ! NONE               ! Some("close")      ! true    |
        `HTTP/1.1`       ! Some("Keep-Alive") ! NONE               ! NONE               ! false   |
        `HTTP/1.0`       ! NONE               ! NONE               ! NONE               ! true    |
        `HTTP/1.0`       ! Some("close")      ! NONE               ! NONE               ! true    |
        `HTTP/1.0`       ! Some("Keep-Alive") ! NONE               ! Some("Keep-Alive") ! false   |
        `HTTP/1.1`       ! NONE               ! Some("close")      ! Some("close")      ! true    |
        `HTTP/1.0`       ! Some("close")      ! Some("Keep-Alive") ! Some("Keep-Alive") ! false   |> {

          (reqProto, reqCH, resCH, renCH, close) => Context(
            response = HttpResponse(200, headers = resCH.map(h => List(HttpHeaders.Connection(h))).getOrElse(Nil)),
            requestProtocol = reqProto,
            requestConnectionHeader = reqCH
          ) must beRenderedTo(
            content = "HTTP/1.1 200 OK\n" +
                      renCH.map("Connection: " + _ + "\n").getOrElse("") +
                      "Server: spray-can/1.0.0\n" +
                      "Date: Thu, 25 Aug 2011 09:10:29 GMT\n" +
                      "Content-Length: 0\n\n",
            close = close
          )
        }
      }
    }
  }

  case class Context(
    response: HttpResponsePart,
    requestMethod: HttpMethod = HttpMethods.GET,
    requestProtocol: HttpProtocol = `HTTP/1.1`,
    requestConnectionHeader: Option[String] = None,
    chunkless: Boolean = false
  )

  def beRenderedTo(content: String, close: Boolean) = {
    beEqualTo(content.stripMargin.replace(EOL, "\r\n") -> close) ^^ { ctx: Context =>
      import ctx._
      val renderer = new ResponseRenderer("spray-can/1.0.0", chunkless, 256) {
        override val dateTimeNow = DateTime(2011, 8, 25, 9,10,29) // provide a stable date for testing
      }
      val sb = new java.lang.StringBuilder
      val RenderedMessagePart(buffers, closeAfterWrite) = renderer.render {
        HttpResponsePartRenderingContext(response, requestMethod, requestProtocol, requestConnectionHeader)
      }
      buffers.foreach { buf => while (buf.remaining > 0) sb.append(buf.get.toChar) }
      sb.toString -> closeAfterWrite
    }
  }

}