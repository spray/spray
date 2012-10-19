/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.httpx

import spray.http._


package object unmarshalling {

  type Deserialized[T] = Either[DeserializationError, T]
  type FromStringDeserializer[T] = Deserializer[String, T]
  type FromStringOptionDeserializer[T] = Deserializer[Option[String], T]
  type Unmarshaller[T] = Deserializer[HttpEntity, T]
  type FromEntityOptionUnmarshaller[T] = Deserializer[Option[HttpEntity], T]

  def unmarshal[T](implicit um: Unmarshaller[T]) = um
  def unmarshalUnsafe[T :Unmarshaller](entity: HttpEntity): T = unmarshal.apply(entity) match {
    case Right(value) => value
    case Left(error) => sys.error(error.toString)
  }

  implicit def formFieldExtractor(form: HttpForm) = FormFieldExtractor(form)
  implicit def pimpHttpEntity(entity: HttpEntity) = new PimpedHttpEntity(entity)
  implicit def pimpHttpBodyPart(bodyPart: BodyPart) = new PimpedHttpEntity(bodyPart.entity)

  class PimpedHttpEntity(entity: HttpEntity) {
    def as[T](implicit unmarshaller: Unmarshaller[T]): Deserialized[T] = unmarshaller(entity)
  }

  // should actually live in file "Unmarshaller.scala"
  // but can't due to https://issues.scala-lang.org/browse/SI-5031
  // will move back once the issue is fixed
  object Unmarshaller {
    def apply[T](unmarshalFrom: ContentTypeRange*)(f: PartialFunction[HttpEntity, T]): Unmarshaller[T] =
      new SimpleUnmarshaller[T] {
        val canUnmarshalFrom = unmarshalFrom
        def unmarshal(entity: HttpEntity) =
          if (f.isDefinedAt(entity)) protect(f(entity)) else Left(ContentExpected)
      }

    def delegate[A, B](unmarshalFrom: ContentTypeRange*)(f: A => B)(implicit mb: Unmarshaller[A]): Unmarshaller[B] =
      new SimpleUnmarshaller[B] {
        val canUnmarshalFrom = unmarshalFrom
        def unmarshal(entity: HttpEntity) = mb(entity).right.flatMap(a => protect(f(a)))
      }

    def forNonEmpty[T](implicit um: Unmarshaller[T]): Unmarshaller[T] =
      new Unmarshaller[T] {
        def apply(entity: HttpEntity) = if (entity.isEmpty) Left(ContentExpected) else um(entity)
      }
  }
}
