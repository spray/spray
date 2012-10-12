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

package spray.httpx.unmarshalling

import spray.http._


trait MetaUnmarshallers {

  implicit def formUnmarshaller(implicit fdum: Unmarshaller[FormData], mpfdum: Unmarshaller[MultipartFormData]) =
    new Unmarshaller[HttpForm] {
      def apply(entity: HttpEntity) = fdum(entity).left.flatMap {
        case UnsupportedContentType(error1) => mpfdum(entity).left.map {
          case UnsupportedContentType(error2) => UnsupportedContentType(error1 + " or " + error2)
          case error => error
        }
        case error => Left(error)
      }
    }

}

object MetaUnmarshallers extends MetaUnmarshallers