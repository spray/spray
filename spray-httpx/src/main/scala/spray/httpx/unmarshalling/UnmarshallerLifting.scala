/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import spray.http._

trait UnmarshallerLifting {

  implicit def fromRequestUnmarshaller[T](implicit um: FromMessageUnmarshaller[T]): FromRequestUnmarshaller[T] =
    new FromRequestUnmarshaller[T] {
      def apply(request: HttpRequest): Deserialized[T] = um(request)
    }

  implicit def fromResponseUnmarshaller[T](implicit um: FromMessageUnmarshaller[T]): FromResponseUnmarshaller[T] =
    new FromResponseUnmarshaller[T] {
      def apply(response: HttpResponse): Deserialized[T] = um(response)
    }

  implicit def fromMessageUnmarshaller[T](implicit um: Unmarshaller[T]): FromMessageUnmarshaller[T] =
    new FromMessageUnmarshaller[T] {
      def apply(msg: HttpMessage): Deserialized[T] = um(msg.entity)
    }
}

object UnmarshallerLifting extends UnmarshallerLifting
