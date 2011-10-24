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

package cc.spray.can

import org.specs2._
import HttpProtocols._
import matcher.DataTables
import java.nio.ByteBuffer

class ResponsePreparerSpec extends Specification with ResponsePreparer with DataTables { def is =

  "The response preparation logic should properly render"   ^
    "a response with status 200, no headers and no body"    ! e1^
    "a response with status 304, a few headers and no body" ! e2^
    "a response with status 400, a few headers and a body"  ! e3^
    "a non-keepalive HTTP/1.0 message"                      ! e4^
    "a chunked response without body"                       ! e5^
    "a chunked response with body"                          ! e6^
    "a response chunk"                                      ! e7^
    "a terminating response chunk"                          ! e8^
                                                            end^
  "The 'Connection' header should be rendered correctly"    ! e9

  def e1 = prep() {
    HttpResponse(200, Nil)
  } mustEqual prep {
    """|HTTP/1.1 200 OK
       |Server: spray-can/1.0.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Content-Length: 0
       |
       |""" -> false
  }

  def e2 = prep() {
    HttpResponse(304, List(
      HttpHeader("X-Fancy", "of course"),
      HttpHeader("Age", "0")
    ))
  } mustEqual prep {
    """|HTTP/1.1 304 Not Modified
       |X-Fancy: of course
       |Age: 0
       |Server: spray-can/1.0.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Content-Length: 0
       |
       |""" -> false
  }

  def e3 = prep() {
    HttpResponse(
      status = 400,
      headers = List(HttpHeader("Age", "30"), HttpHeader("Connection", "Keep-Alive")),
      body = "Small f*ck up overhere!".getBytes("ASCII"),
      protocol = `HTTP/1.0`
    )
  } mustEqual prep {
    """|HTTP/1.0 400 Bad Request
       |Age: 30
       |Connection: Keep-Alive
       |Server: spray-can/1.0.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Content-Length: 23
       |
       |Small f*ck up overhere!""" -> false
  }

  def e4 = prep() {
    HttpResponse(
      status = 200,
      headers = List(HttpHeader("Age", "30"), HttpHeader("Cache-Control", "public")),
      body = "Small f*ck up overhere!".getBytes("ASCII"),
      protocol = `HTTP/1.0`
    )
  } mustEqual prep {
    """|HTTP/1.0 200 OK
       |Age: 30
       |Cache-Control: public
       |Server: spray-can/1.0.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |
       |Small f*ck up overhere!""" -> true
  }

  def e5 = prep(reqConnectionHeader = Some("close"), chunked = true) {
    HttpResponse(200, List(HttpHeader("Age", "30")))
  } mustEqual prep {
    """|HTTP/1.1 200 OK
       |Age: 30
       |Connection: close
       |Server: spray-can/1.0.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Transfer-Encoding: chunked
       |
       |""" -> true
  }

  def e6 = prep(chunked = true) {
    HttpResponse().withBody("Yahoooo")
  } mustEqual prep {
    """|HTTP/1.1 200 OK
       |Server: spray-can/1.0.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Transfer-Encoding: chunked
       |
       |7
       |Yahoooo
       |""" -> false
  }

  def e7 = decode(
    prepareChunk(
      List(ChunkExtension("key", "value"), ChunkExtension("another", "tl;dr")),
      "body123".getBytes("ISO-8859-1")
    )
  ) mustEqual prep {
    """|7;key=value;another="tl;dr"
       |body123
       |"""
  }

  def e8 = decode(
    prepareFinalChunk(Nil, List(HttpHeader("Age", "30"), HttpHeader("Cache-Control", "public")))
  ) mustEqual prep {
    """|0
       |Age: 30
       |Cache-Control: public
       |
       |"""
  }

  val NONE: Option[String] = None
  
  def e9 =
    "Client Version" | "Request"          | "Response"         | "Rendered"         | "Close" |
    `HTTP/1.1`       ! NONE               ! NONE               ! NONE               ! false   |
    `HTTP/1.1`       ! Some("close")      ! NONE               ! Some("close")      ! true    |
    `HTTP/1.1`       ! Some("Keep-Alive") ! NONE               ! NONE               ! false   |
    `HTTP/1.0`       ! NONE               ! NONE               ! NONE               ! true    |
    `HTTP/1.0`       ! Some("close")      ! NONE               ! NONE               ! true    |
    `HTTP/1.0`       ! Some("Keep-Alive") ! NONE               ! Some("Keep-Alive") ! false   |
    `HTTP/1.1`       ! NONE               ! Some("close")      ! Some("close")      ! true    |
    `HTTP/1.0`       ! Some("close")      ! Some("Keep-Alive") ! Some("Keep-Alive") ! false   |> {
      (reqProto, reqCH, resCH, renCH, close) =>
      prep(reqProto, reqCH) {
        HttpResponse(200, resCH.map(h => List(HttpHeader("Connection", h))).getOrElse(Nil))
      } mustEqual prep {
        "HTTP/1.1 200 OK\n" +
        renCH.map("Connection: " + _ + "\n").getOrElse("") +
        "Server: spray-can/1.0.0\n" +
        "Date: Thu, 25 Aug 2011 09:10:29 GMT\n" +
        "Content-Length: 0\n\n" -> close
      }
    }

  def prep(reqProtocol: HttpProtocol = `HTTP/1.1`, reqConnectionHeader: Option[String] = None, chunked: Boolean = false)
          (response: HttpResponse) = {
    val sb = new java.lang.StringBuilder()
    val (buffers, closeAfterWrite) = {
      if (chunked) prepareChunkedResponseStart(RequestLine(protocol = reqProtocol), response, reqConnectionHeader)
      else prepareResponse(RequestLine(protocol = reqProtocol), response, reqConnectionHeader)
    }
    sb.append(decode(buffers))
    sb.toString -> closeAfterWrite
  }

  def decode(buffers: List[ByteBuffer]) = {
    val sb = new java.lang.StringBuilder()
    buffers.foreach { buf =>
      sb.append(new String(buf.array, "ASCII"))
    }
    sb.toString
  }

  def prep(t: (String, Boolean)): (String, Boolean) = t._1.stripMargin.replace("\n", "\r\n") -> t._2

  def prep(s: String): String = s.stripMargin.replace("\n", "\r\n")

  override val dateTimeNow = DateTime(2011, 8, 25, 9,10,29) // provide a stable date for testing

  protected def serverHeader = "spray-can/1.0.0"
}