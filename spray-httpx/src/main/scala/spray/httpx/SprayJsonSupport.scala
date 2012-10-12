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

import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import spray.json._
import spray.http._
import MediaTypes._

/**
 * A trait providing automatic to and from JSON marshalling/unmarshalling using an in-scope *spray-json* protocol.
 * Note that *spray-httpx* does not have an automatic dependency on *spray-json*.
 * You'll need to provide the appropriate *spray-json* artifacts yourself.
 */
trait SprayJsonSupport {

  implicit def sprayJsonUnmarshaller[T :RootJsonReader] =
    Unmarshaller[T](`application/json`) {
      case x: HttpBody =>
        val json = JsonParser(x.asString)
        jsonReader[T].read(json)
    }

  implicit def sprayJsonMarshaller[T](implicit writer: RootJsonWriter[T], printer: JsonPrinter = PrettyPrinter) =
    Marshaller.delegate[T, String](`application/json`) { value =>
      val json = writer.write(value)
      printer(json)
    }
}

object SprayJsonSupport extends SprayJsonSupport