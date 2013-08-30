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

import org.json4s.jackson.Serialization
import org.json4s.Formats
import spray.httpx.marshalling.{ Marshaller, MetaMarshallers }
import spray.httpx.unmarshalling.Unmarshaller
import spray.http._

trait Json4sJacksonSupport extends MetaMarshallers {
  implicit def json4sJacksonFormats: Formats

  implicit def json4sUnmarshaller[T: Manifest] =
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty ⇒ Serialization.read[T](x.asString(defaultCharset = HttpCharsets.`UTF-8`))
    }

  implicit def json4sMarshaller[T <: AnyRef] =
    Marshaller.delegate[T, String](ContentTypes.`application/json`)(Serialization.write(_))
}
