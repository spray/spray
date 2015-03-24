/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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
  type FromBodyPartOptionUnmarshaller[T] = Deserializer[Option[BodyPart], T]
  type FromMessageUnmarshaller[T] = Deserializer[HttpMessage, T] // source-quote-FromMessageUnmarshaller
  type FromRequestUnmarshaller[T] = Deserializer[HttpRequest, T] // source-quote-FromRequestUnmarshaller
  type FromResponseUnmarshaller[T] = Deserializer[HttpResponse, T] // source-quote-FromResponseUnmarshaller

  implicit def formFieldExtractor(form: HttpForm) = FormFieldExtractor(form)
  implicit def pimpBodyPart(bodyPart: BodyPart) = new PimpedHttpEntity(bodyPart.entity)

  // TODO: remove the following pimps after merging spray-http and spray-httpx

  implicit class PimpedHttpEntity(entity: HttpEntity) {
    def as[T](implicit unmarshaller: Unmarshaller[T]): Deserialized[T] = unmarshaller(entity)
  }

  implicit class PimpedHttpMessage(msg: HttpMessage) {
    def as[T](implicit unmarshaller: FromMessageUnmarshaller[T]): Deserialized[T] = unmarshaller(msg)
  }

  implicit class PimpedHttpRequest(request: HttpRequest) {
    def as[T](implicit unmarshaller: FromRequestUnmarshaller[T]): Deserialized[T] = unmarshaller(request)
  }

  implicit class PimpedHttpResponse(response: HttpResponse) {
    def as[T](implicit unmarshaller: FromResponseUnmarshaller[T]): Deserialized[T] = unmarshaller(response)
  }
}
