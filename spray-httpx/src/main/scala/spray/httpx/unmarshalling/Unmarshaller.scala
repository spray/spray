/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.httpx.unmarshalling

import spray.http.{ HttpEntity, ContentTypeRange }

object Unmarshaller {
  def apply[T](unmarshalFrom: ContentTypeRange*)(f: PartialFunction[HttpEntity, T]): Unmarshaller[T] =
    new SimpleUnmarshaller[T] {
      val canUnmarshalFrom = unmarshalFrom
      def unmarshal(entity: HttpEntity) =
        if (f.isDefinedAt(entity)) protect(f(entity)) else Left(ContentExpected)
    }

  def delegate[A, B](unmarshalFrom: ContentTypeRange*)(f: A ⇒ B)(implicit mb: Unmarshaller[A]): Unmarshaller[B] =
    new SimpleUnmarshaller[B] {
      val canUnmarshalFrom = unmarshalFrom
      def unmarshal(entity: HttpEntity) = mb(entity).right.flatMap(a ⇒ protect(f(a)))
    }

  def forNonEmpty[T](implicit um: Unmarshaller[T]): Unmarshaller[T] =
    new Unmarshaller[T] {
      def apply(entity: HttpEntity) = if (entity.isEmpty) Left(ContentExpected) else um(entity)
    }
}
