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
package marshalling

import http._
import MediaTypes._
import HttpCharsets._
import xml.NodeSeq
import test.AbstractSprayTest
import utils.FormContent

class MultipartUnmarshallersSpec extends AbstractSprayTest {
  
  "The MultiPartFormDataUnmarshaller" should {
    "correctly unmarshal 'multipart/mixed' content with one empty part" in {
      test(HttpRequest(content = Some(HttpContent(ContentType(new `multipart/mixed`(Some("XYZABC"))),
        """|--XYZABC
           |--XYZABC--""".stripMargin)))) {
        content(as[MultipartContent]) { echoComplete }
      }.response.content.as[String] mustEqual Right {
        "MultipartContent(" +
          "Vector(" +
            "BodyPart(List(),Some(HttpContent(ContentType(MediaType(text/plain),Some(HttpCharset(US-ASCII))),)))" +
          ")" +
        ")"
      }
    }
    "correctly unmarshal 'multipart/form-data' content with one part" in {
      test(HttpRequest(content = Some(HttpContent(ContentType(new `multipart/form-data`(Some("-"))),
        """|---
           |Content-type: text/plain; charset=UTF8
           |content-disposition: form-data; name="email"
           |
           |test@there.com
           |-----""".stripMargin)))) {
        content(as[MultipartContent]) { echoComplete }
      }.response.content.as[String] mustEqual Right {
        "MultipartContent(" +
          "Vector(" +
            "BodyPart(" +
              "List(" +
                "Content-Type: text/plain; charset=UTF-8, " +
                "Content-Disposition: form-data; name=\"email\"" +
              ")," +
              "Some(HttpContent(ContentType(MediaType(text/plain),Some(HttpCharset(UTF-8))),test@there.com))" +
            ")" +
          ")" +
        ")"
      }
    }
    "correctly unmarshal multipart content with two different parts" in {
      test(HttpRequest(content = Some(HttpContent(ContentType(new `multipart/mixed`(Some("12345"))),
        """|--12345
           |
           |first part, with a trailing linebreak
           |
           |--12345
           |Content-Type: application/octet-stream
           |Content-Transfer-Encoding: binary
           |
           |filecontent
           |--12345--""".stripMargin)))) {
        content(as[MultipartContent]) { echoComplete }
      }.response.content.as[String] mustEqual Right {
        "MultipartContent(" +
          "Vector(" +
            "BodyPart(" +
              "List()," +
              "Some(HttpContent(ContentType(MediaType(text/plain),Some(HttpCharset(US-ASCII)))," +
                "first part, with a trailing linebreak\n))" +
            "), " +
            "BodyPart(" +
              "List(Content-Type: application/octet-stream, Content-Transfer-Encoding: binary)," +
              "Some(HttpContent(ContentType(MediaType(application/octet-stream),None),filecontent))" +
            ")" +
          ")" +
        ")"
      }
    }
    "reject illegal multipart content" in {
      test(HttpRequest(content = Some(HttpContent(ContentType(new `multipart/mixed`(Some("12345"))), "--noob")))) {
        content(as[MultipartContent]) { echoComplete }
      }.rejections mustEqual Set(MalformedRequestContentRejection("Could not parse multipart content: Missing start boundary"))
    }
  }

}