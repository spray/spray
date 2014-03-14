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

package spray.httpx

import java.lang.reflect.InvocationTargetException

import net.liftweb.json._
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import spray.http._

/**
 * A trait providing automatic to and from JSON marshalling/unmarshalling for case classes via lift-json.
 * Note that *spray-routing* does not have an automatic dependency on *lift-json*. You'll need to provide the
 * appropriate *lift-json* artifacts yourself.
 */
trait LiftJsonSupport {

  /**
   * The `Formats` to use for (de)serialization.
   */
  implicit def liftJsonFormats: Formats

  implicit def liftJsonUnmarshaller[T: Manifest] =
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty ⇒
        val jsonSource = x.asString(defaultCharset = HttpCharsets.`UTF-8`)
        try parse(jsonSource).extract[T]
        catch {
          case MappingException("unknown error", ite: InvocationTargetException) ⇒ throw ite.getCause
        }
    }

  implicit def liftJsonMarshaller[T <: AnyRef] =
    Marshaller.delegate[T, String](ContentTypes.`application/json`)(Serialization.write(_))
}
