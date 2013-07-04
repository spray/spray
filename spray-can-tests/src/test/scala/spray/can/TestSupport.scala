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

package spray.can

import com.typesafe.config.ConfigFactory
import spray.can.parsing.ParserSettings
import spray.util._
import spray.http._
import HttpHeaders._
import MediaTypes._

object TestSupport {

  def defaultParserSettings = ParserSettings(ConfigFactory.load() getConfig "spray.can.parsing")

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
      `Content-Length`(0),
      `Date`(DateTime(2011, 8, 25, 9, 10, 29)),
      `Server`("spray/1.0")))

  def response(content: String, additionalHeaders: HttpHeader*) = HttpResponse(
    status = 200,
    headers = additionalHeaders.toList ::: List(
      `Content-Type`(`text/plain`),
      `Content-Length`(content.length),
      `Date`(DateTime(2011, 8, 25, 9, 10, 29)),
      `Server`("spray/1.0")),
    entity = content)

  def rawResponse = prep {
    """|HTTP/1.1 200 OK
       |Server: spray/1.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Content-Length: 0
       |
       |"""
  }

  def rawResponse(content: String, additionalHeaders: String = "") = prep {
    """|HTTP/1.1 200 OK
       |Server: spray/1.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Content-Length: %s
       |Content-Type: text/plain
       |%s
       |%s""".format(content.length, if (additionalHeaders.isEmpty) "" else additionalHeaders + '\n', content)
  }

  val chunkedRequestStart = prep {
    """GET / HTTP/1.1
      |Host: test.com
      |Content-Type: text/plain
      |Transfer-Encoding: chunked
      |
      |"""
  }

  val chunkedResponseStart = prep {
    """HTTP/1.1 200 OK
      |Transfer-Encoding: chunked
      |Server: spray/1.0
      |Date: Thu, 25 Aug 2011 09:10:29 GMT
      |Content-Type: text/plain
      |
      |"""
  }

  val messageChunk = prep {
    """7
      |body123
      |"""
  }

  val chunkedMessageEnd = prep {
    """0
      |Age: 30
      |Cache-Control: public
      |
      |"""
  }

  def prep(s: String) = s.stripMargin.replace(EOL, "\r\n")

  def wipeDate(string: String) =
    string.fastSplit('\n').map {
      case s if s.startsWith("Date:") ⇒ "Date: XXXX\r"
      case s                          ⇒ s
    }.mkString("\n")
}
