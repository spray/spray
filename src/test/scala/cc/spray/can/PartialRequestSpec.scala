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

import org.specs2.mutable.Specification
import java.nio.ByteBuffer
import annotation.tailrec
import Constants._

class PartialRequestSpec extends Specification {

  val HH = HttpHeader

  "The request parsing logic" should {

    "properly parse request example 1" in {
      test {
        """|GET / HTTP/1.1
           |Host: api.example.com
           |
           |"""
      } mustEqual ("GET", "/", List(HH("Host", "api.example.com")), "")
    }

    "properly parse request example 2" in {
      test {
        """|POST /resource/yes HTTP/1.1
           |User-Agent: curl/7.19.7 xyz
           |Accept:*/*
           |Content-Length    : 17
           |
           |Shake your BOODY!"""
      } mustEqual ("POST", "/resource/yes", List(
        HH("Content-Length", "17"),
        HH("Accept", "*/*"),
        HH("User-Agent", "curl/7.19.7 xyz")
      ), "Shake your BOODY!")
    }

    "properly parse request example 3" in {
      test {
        """|DELETE /abc HTTP/1.1
           |User-Agent: curl/7.19.7
           | abc
           |    xyz
           |Accept
           | : */*  """ + """
           |
           |"""
      } mustEqual ("DELETE", "/abc", List(
        HH("Accept", "*/*"),
        HH("User-Agent", "curl/7.19.7 abc xyz")
      ), "")
    }

  }

  def test(request: String) = {
    val req = request.stripMargin.replace("\n", "\r\n")
    val buf = ByteBuffer.wrap(req.getBytes(US_ASCII))

    @tailrec
    def runAgainst(req: PartialRequest): Any = req.read(buf) match {
      case CompletePartialRequest(method, uri, headers, body) => (method, uri, headers, new String(body, US_ASCII))
      case x: ErrorRequest => x
      case x => runAgainst(x)
    }

    runAgainst(EmptyRequest)
  }

}