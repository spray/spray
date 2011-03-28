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
package builders

import http._
import org.specs.Specification
import HttpHeaders._
import HttpMethods._
import HttpStatusCodes._
import MediaTypes._
import Charsets._
import test.SprayTest
import marshalling.{UnmarshallerBase, MarshallerBase}
import xml.{XML, NodeSeq}

class UnMarshallingBuildersSpec extends Specification with SprayTest with ServiceBuilder {
  
  implicit object IntUnmarshaller extends UnmarshallerBase[Int] {
    val canUnmarshalFrom = ContentTypeRange(`text/xml`, `ISO-8859-2`) ::
                           ContentTypeRange(`text/html`) ::
                           ContentTypeRange(`application/xhtml+xml`) :: Nil

    def unmarshal(content: HttpContent) = protect { XML.load(content.inputStream).text.toInt }
  }
  
  implicit object IntMarshaller extends MarshallerBase[Int] {
    val canMarshalTo = ContentType(`application/xhtml+xml`) :: ContentType(`text/xml`, `UTF-8`) :: Nil
    def marshal(value: Int, contentType: ContentType) = NodeSeqMarshaller.marshal(<int>{value}</int>, contentType)
  }
  
  "The 'contentAs' directive" should {
    "extract an object from the requests HttpContent using the in-scope Unmarshaller" in {
      test(HttpRequest(PUT, content = Some(HttpContent(ContentType(`text/xml`), "<p>cool</p>")))) {
        contentAs[NodeSeq] { xml => _.complete(xml) }
      }.response.content.as[NodeSeq] mustEqual Right(<p>cool</p>) 
    }
    "return a RequestEntityExpectedRejection rejection if the request has no entity" in {
      test(HttpRequest(PUT)) {
        contentAs[NodeSeq] { _ => fail("Should not run") }
      }.rejections mustEqual Set(RequestEntityExpectedRejection)
    }
    "return an UnsupportedRequestContentTypeRejection if no matching unmarshaller is in scope" in {
      test(HttpRequest(PUT, content = Some(HttpContent(ContentType(`text/css`), "<p>cool</p>")))) {
        contentAs[NodeSeq] { _ => fail("Should not run") }
      }.rejections mustEqual Set(UnsupportedRequestContentTypeRejection(NodeSeqUnmarshaller.canUnmarshalFrom))
    }
  }
  
  "The 'optionalContentAs' directive" should {
    "extract an object from the requests HttpContent using the in-scope Unmarshaller" in {
      test(HttpRequest(PUT, content = Some(HttpContent(ContentType(`text/xml`), "<p>cool</p>")))) {
        optionalContentAs[NodeSeq] { optXml => _.complete(optXml.get) }
      }.response.content.as[NodeSeq] mustEqual Right(<p>cool</p>) 
    }
    "extract None if the request has no entity" in {
      test(HttpRequest(PUT)) {
        optionalContentAs[NodeSeq] { optXml => _.complete(optXml.toString) }
      }.response.content.as[String] mustEqual Right("None")
    }
    "return an UnsupportedRequestContentTypeRejection if no matching unmarshaller is in scope" in {
      test(HttpRequest(PUT, content = Some(HttpContent(ContentType(`text/css`), "<p>cool</p>")))) {
        optionalContentAs[NodeSeq] { _ => fail("Should not run") }
      }.rejections mustEqual Set(UnsupportedRequestContentTypeRejection(NodeSeqUnmarshaller.canUnmarshalFrom))
    }
  }
  
  "The 'produces' directive" should {
    "provide a completion function converting custom objects to HttpContent using the in-scope marshaller" in {
      test(HttpRequest(GET)) {
        produces[Int] { produce =>
          _ => produce(42)
        }
      }.response.content mustEqual Some(HttpContent(ContentType(`application/xhtml+xml`), "<int>42</int>"))
    }
    "return a UnacceptedResponseContentTypeRejection rejection if no acceptable marshaller is in scope" in {
      test(HttpRequest(GET, headers = List(`Accept`(`text/css`)))) {
        produces[Int] { produce =>
          _ => produce(42)
        }
      }.rejections mustEqual Set(UnacceptedResponseContentTypeRejection(IntMarshaller.canMarshalTo))
    }
  }
  
  "The 'handledBy' directive" should {
    "support proper round-trip content unmarshalling/marshalling to and from a function" in {
      test(HttpRequest(PUT, headers = List(Accept(`text/xml`)),
        content = Some(HttpContent(ContentType(`text/html`), "<int>42</int>")))) {
        handledBy { (x: Int) => x * 2 }
      }.response.content mustEqual Some(HttpContent(ContentType(`text/xml`, `UTF-8`), "<int>84</int>"))
    }
    "result in UnsupportedRequestContentTypeRejection rejection if there is no unmarshaller supporting the requests charset" in {
      test(HttpRequest(PUT, headers = List(Accept(`text/xml`)),
        content = Some(HttpContent(ContentType(`text/xml`, `UTF-8`), "<int>42</int>")))) {
        handledBy { (x: Int) => x * 2 }
      }.rejections mustEqual Set(UnsupportedRequestContentTypeRejection(IntUnmarshaller.canUnmarshalFrom))
    }
    "result in an UnacceptedResponseContentTypeRejection rejection if there is no marshaller supporting the requests Accept-Charset header" in {
      test(HttpRequest(PUT, headers = List(Accept(`text/xml`), `Accept-Charset`(`UTF-16`)),
        content = Some(HttpContent(ContentType(`text/html`), "<int>42</int>")))) {
        handledBy { (x: Int) => x * 2 }
      }.rejections mustEqual Set(UnacceptedResponseContentTypeRejection(IntMarshaller.canMarshalTo))
    }
  }
  
}