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
  type ContentTypeSelector = ContentType => Option[ContentType]
  type Marshaller[A] = ContentTypeSelector => Marshalling[A]

  def marshaller[T](implicit m: Marshaller[T]) = m
  def unmarshaller[T](implicit um: Unmarshaller[T]) = um
  def deserializer[A, B](implicit converter: Deserializer[A, B]) = converter
  def fromStringOptionDeserializer[T](implicit converter: FromStringOptionDeserializer[T]) = converter

}