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
package http

import java.util.Arrays
import HttpCharsets._
import MediaTypes._
import typeconversion._

class HttpContent private[http](val contentType: ContentType, val buffer: Array[Byte]) {
  def withContentType(contentType: ContentType) = new HttpContent(contentType, buffer)
  def withBuffer(buffer: Array[Byte]) = new HttpContent(contentType, buffer)

  override def toString = "HttpContent(" + contentType + ',' + new String(buffer, contentType.charset.getOrElse(`ISO-8859-1`).nioCharset) + ')'
  override def hashCode = contentType.## * 31 + Arrays.hashCode(buffer)
  override def equals(obj: Any) = obj match {
    case o: HttpContent => contentType == o.contentType && Arrays.equals(buffer, o.buffer)
    case _ => false
  }
}

object HttpContent {
  def apply(string: String): HttpContent = apply(ContentType(`text/plain`, `ISO-8859-1`), string)
  
  def apply(contentType: ContentType, string: String): HttpContent = {
    apply(contentType, string.getBytes(contentType.charset.getOrElse(`ISO-8859-1`).nioCharset))
  }
  
  def apply(contentType: ContentType, buffer: Array[Byte]): HttpContent = new HttpContent(contentType, buffer)

  implicit def pimpHttpContentWithAs1(c: HttpContent): HttpContentExtractor = new HttpContentExtractor(Some(c))
  implicit def pimpHttpContentWithAs2(c: Option[HttpContent]): HttpContentExtractor = new HttpContentExtractor(c)

  class HttpContentExtractor(content: Option[HttpContent]) {
    def as[A](implicit unmarshaller: Unmarshaller[A]): Either[DeserializationError, A] = unmarshaller(content)

    def formField(fieldName: String): Either[DeserializationError, FormField] = {
      import DefaultUnmarshallers._
      content.as[FormData] match {
        case Right(formData) => Right {
          new UrlEncodedFormField {
            val raw = formData.fields.get(fieldName)
            def as[A: FormFieldConverter] = converter[A].urlEncodedFieldConverter match {
              case Some(conv) => conv(raw)
              case None => Left(UnsupportedContentType("Field '" + fieldName +
                                "' can only be read from 'multipart/form-data' form content"))
            }
          }
        }
        case Left(_: UnsupportedContentType) => content.as[MultipartFormData].right.map { data =>
          new MultipartFormField {
            val raw = data.parts.get(fieldName)
            def as[A: FormFieldConverter] = converter[A].multipartFieldConverter match {
              case Some(conv) => conv(raw.flatMap(_.content))
              case None => Left(UnsupportedContentType("Field '" + fieldName +
                                "' can only be read from 'application/x-www-form-urlencoded' form content"))
            }
          }
        }
        case Left(error) => Left(error)
      }
    }

    private def converter[A](implicit conv: FormFieldConverter[A]) = conv
  }
}

sealed trait FormField {
  def as[A :FormFieldConverter]: Either[DeserializationError, A]
}

trait UrlEncodedFormField extends FormField {
  def raw: Option[String]
}

trait MultipartFormField extends FormField {
  def raw: Option[BodyPart]
}

