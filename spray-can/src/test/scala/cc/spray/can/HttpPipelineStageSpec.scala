/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

import cc.spray.io.test.PipelineStageTest
import model.{HttpHeader, HttpResponse, HttpRequest}

trait HttpPipelineStageSpec extends PipelineStageTest {

  def request(content: String = "") = HttpRequest().withBody(content)

  def rawRequest(content: String = "") = prep {
    """|GET / HTTP/1.1
       |Host: example.com:8080
       |User-Agent: spray/1.0
       |%s
       |%s"""
  }.format(if (content.isEmpty) "" else "Content-Length: %s\r\n".format(content.length), content)

  def response(content: String = "") = HttpResponse(
    status = 200,
    headers = List(
      HttpHeader("content-length", content.length.toString),
      HttpHeader("date", "Thu, 25 Aug 2011 09:10:29 GMT"),
      HttpHeader("server", "spray/1.0")
    )
  ).withBody(content)

  def rawResponse(content: String = "") = prep {
    """|HTTP/1.1 200 OK
       |Server: spray/1.0
       |Date: Thu, 25 Aug 2011 09:10:29 GMT
       |Content-Length: %s
       |
       |%s"""
  }.format(content.length, content)

  def prep(s: String) = s.stripMargin.replace("\n", "\r\n")
}
