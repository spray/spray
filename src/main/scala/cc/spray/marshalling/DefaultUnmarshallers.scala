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
import MediaRanges._
import xml.{XML, NodeSeq}

trait DefaultUnmarshallers {
  
  implicit object StringUnmarshaller extends UnmarshallerBase[String] {
    val canUnmarshalFrom = List(ContentTypeRange(`text/*`))

    def unmarshal(content: HttpContent) = content.contentType.charset match {
      case Some(cs) => Right(new String(content.buffer, cs.nioCharset))
      case None => throw new IllegalStateException // text content should always have a Charset set
    }
  }
  
  implicit object NodeSeqUnmarshaller extends UnmarshallerBase[NodeSeq] {
    val canUnmarshalFrom = ContentTypeRange(`text/xml`) ::
                           ContentTypeRange(`text/html`) ::
                           ContentTypeRange(`application/xhtml+xml`) :: Nil

    def unmarshal(content: HttpContent) = protect { XML.load(content.inputStream) }
  }
  
  implicit def pimpHttpContentWithAs1(c: HttpContent): HttpContentExtractor = new HttpContentExtractor(Some(c)) 
  implicit def pimpHttpContentWithAs2(c: Option[HttpContent]): HttpContentExtractor = new HttpContentExtractor(c)
  
  class HttpContentExtractor(content: Option[HttpContent]) {
    def as[A](implicit unmarshaller: Unmarshaller[A]): Either[Rejection, A] = content match {
      case Some(httpContent) => unmarshaller(httpContent.contentType) match {
        case UnmarshalWith(converter) => converter(httpContent)
        case CantUnmarshal(onlyFrom) => Left(UnsupportedRequestContentTypeRejection(onlyFrom))
      }
      case None => Left(RequestEntityExpectedRejection)
    }
  }
  
}

object DefaultUnmarshallers extends DefaultUnmarshallers