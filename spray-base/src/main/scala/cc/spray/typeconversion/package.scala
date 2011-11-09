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

import http._

package object typeconversion {

  type FromStringOptionDeserializer[A] = Deserializer[Option[String], A]
  type Unmarshaller[A] = Deserializer[Option[HttpContent], A]
  type Marshaller[A] = ContentTypeSelector => Marshalling[A]
  type ContentTypeSelector = ContentType => Option[ContentType]

  def marshaller[T](implicit m: Marshaller[T]) = m
  def unmarshaller[T](implicit um: Unmarshaller[T]) = um
  def deserializer[A, B](implicit ds: Deserializer[A, B]) = ds
  def fromStringOptionDeserializer[T](implicit ds: FromStringOptionDeserializer[T]) = ds
  def formFieldConverter[T](implicit ffc: FormFieldConverter[T]) = ffc

  implicit def pimpAnyWithToHttpContent[A :Marshaller](obj: A) = new ToHttpContentPimp(obj)

  class ToHttpContentPimp[A :Marshaller](underlying: A) {
    def toHttpContent: HttpContent = marshaller[A].apply(withExplicitCharset) match {
      case MarshalWith(converter) => converter(underlying)
      case CantMarshal(onlyTo) => throw new IllegalStateException
    }
    def withExplicitCharset(ct: ContentType): Option[ContentType] = Some {
      if (ct.charset.isDefined || !ct.mediaType.isText) ct else ct.copy(charset = Some(HttpCharsets.`ISO-8859-1`))
    }
  }

}