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

package spray.can

import spray.io._
import spray.util._
import spray.http._
import HttpHeaders.RawHeader


trait HttpPipelineStageSpec extends PipelineStageTest {
  val Tell = IOPeer.Tell
  val AckEvent = IOPeer.AckEvent
  val Closed = IOPeer.Closed

  override def extractCommands(commands: List[Command]) =
    super.extractCommands(commands).map {
      case SendString(string) => SendString {
        string.fastSplit('\n').map {
          case s if s.startsWith("Date:") => "Date: XXXX\r"
          case s => s
        }.mkString("\n")
      }
      case x => x
    }

  def request(content: String = "") = HttpRequest().withEntity(content)

  def emptyRawRequest(method: String = "GET") = prep {
    """|%s / HTTP/1.1
       |Host: example.com:8080
       |User-Agent: spray/1.0
       |
       |"""
  }.format(method)

  def rawRequest(content: String, method: String = "GET") = prep {
    """|%s / HTTP/1.1
       |Host: example.com:8080
       |User-Agent: spray/1.0
       |Content-Type: text/plain
       |Content-Length: %s
       |
       |%s"""
  }.format(method, content.length, content)

  def response = HttpResponse(
    status = 200,
    headers = List(
      RawHeader("content-length", "0"),
      RawHeader("date", "Thu, 25 Aug 2011 09:10:29 GMT"),
      RawHeader("server", "spray/1.0")
    )
  )

  def response(content: String) = HttpResponse(
    status = 200,
    headers = List(
      RawHeader("content-type", "text/plain"),
      RawHeader("content-length", content.length.toString),
      RawHeader("date", "Thu, 25 Aug 2011 09:10:29 GMT"),
      RawHeader("server", "spray/1.0")
    ),
    entity = content
  )

  def rawResponse = prep {
    """|HTTP/1.1 200 OK
       |Server: spray/1.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Content-Length: 0
       |
       |"""
  }

  def rawResponse(content: String) = prep {
    """|HTTP/1.1 200 OK
       |Server: spray/1.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Content-Length: %s
       |Content-Type: text/plain
       |
       |%s"""
  }.format(content.length, content)

  def prep(s: String) = s.stripMargin.replace(EOL, "\r\n")
}
