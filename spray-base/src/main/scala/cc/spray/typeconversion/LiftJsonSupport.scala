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
package typeconversion

import http._
import MediaTypes._

import net.liftweb.json.Serialization._
import net.liftweb.json._

/**
 * A trait providing automatic to and from JSON marshalling/unmarshalling for case classes via lift-json.
 * Note that *spray-server* does not have an automatic dependency on *lift-json*. You'll need to provide the
 * appropriate *lift-json* artifacts yourself.
 */
trait LiftJsonSupport {

  /**
   * The `Formats` to use for (de)serialization.
   */
  implicit def liftJsonFormats: Formats

  implicit def liftJsonUnmarshaller[A <: Product :Manifest] = new SimpleUnmarshaller[A] {
    val canUnmarshalFrom = ContentTypeRange(`application/json`) :: Nil
    def unmarshal(content: HttpContent) = protect {
      val jsonSource = DefaultUnmarshallers.StringUnmarshaller(content).right.get
      parse(jsonSource).extract[A]
    }
  }

  implicit def liftJsonMarshaller[A <: AnyRef] = new SimpleMarshaller[A] {
    val canMarshalTo = ContentType(`application/json`) :: Nil
    def marshal(value: A, contentType: ContentType) = {
      val jsonSource = write(value)
      DefaultMarshallers.StringMarshaller.marshal(jsonSource, contentType)
    }
  }
}