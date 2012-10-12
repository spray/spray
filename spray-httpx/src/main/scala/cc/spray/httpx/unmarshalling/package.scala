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
}