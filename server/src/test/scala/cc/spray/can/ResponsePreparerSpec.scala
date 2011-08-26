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
import utils.DateTime

class ResponsePreparerSpec extends Specification with ResponsePreparer {

  "The response preparation logic" should {
    "properly serialize a response" in {
      "with status 200, no headers or body" in {
        prep(HttpResponse(200, Nil)) mustEqual prep {
          """|HTTP/1.1 200 OK
             |Date: Thu, 25 Aug 2011 09:10:29 GMT
             |
             |"""
        }
      }

      "with status 304, a few headers and no body" in {
        prep(HttpResponse(304, List(
          HttpHeader("Age", "0"),
          HttpHeader("Server", "spray-can/1.0")
        ))) mustEqual prep {
          """|HTTP/1.1 304 Not Modified
             |Age: 0
             |Server: spray-can/1.0
             |Date: Thu, 25 Aug 2011 09:10:29 GMT
             |
             |"""
        }
      }

      "with status 400, a few headers and a body" in {
        prep(HttpResponse(400, List(
          HttpHeader("Cache-Control", "public"),
          HttpHeader("Server", "spray-can/1.0")
        ), "Small f*ck up overhere!".getBytes(US_ASCII))) mustEqual prep {
          """|HTTP/1.1 400 Bad Request
             |Cache-Control: public
             |Server: spray-can/1.0
             |Content-Length: 23
             |Date: Thu, 25 Aug 2011 09:10:29 GMT
             |
             |Small f*ck up overhere!"""
        }
      }
    }
  }

  def prep(response: HttpResponse) = {
    val sb = new java.lang.StringBuilder()
    prepare(response).foreach { buf =>
      sb.append(new String(buf.array, US_ASCII))
    }
    sb.toString
  }

  def prep(s: String) = s.stripMargin.replace("\n", "\r\n")

  override val dateTimeNow = DateTime(2011, 8, 25, 9,10,29) // provide a stable date for testing

}