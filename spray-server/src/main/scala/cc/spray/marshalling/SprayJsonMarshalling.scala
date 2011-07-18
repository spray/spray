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
package marshalling

import http._
import MediaTypes._
import json._

/**
 * A trait providing automatic to and from JSON marshalling/unmarshalling using an in-scope *spray-json* protocol.
 * Note that *spray-server* does not have an automatic dependency on *spray-json*. You'll need to provide the
 * appropriate *spray-json* artifacts yourself.
 */
trait SprayJsonMarshalling {

  implicit def sprayJsonUnmarshaller[A :JsonReader]: Unmarshaller[A] = new UnmarshallerBase[A] {
    def canUnmarshalFrom = ContentTypeRange(`application/json`) :: Nil

    def unmarshal(content: HttpContent) = protect {
      val jsonSource = DefaultUnmarshallers.StringUnmarshaller.unmarshal(content).right.get
      val json = JsonParser(jsonSource)
      jsonReader[A].read(json)
    }
  }

  implicit def sprayJsonMarshaller[A :JsonWriter]: Marshaller[A] = new MarshallerBase[A] {
    val canMarshalTo = ContentType(`application/json`) :: Nil

    lazy val printer = if (SpraySettings.CompactJsonPrinting) CompactPrinter else PrettyPrinter

    def marshal(value: A, contentType: ContentType) = {
      val json = value.toJson
      val jsonSource = printer(json)
      DefaultMarshallers.StringMarshaller.marshal(jsonSource, contentType)
    }
  }
}