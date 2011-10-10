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

trait FormFieldConverter[A] {
  def urlEncodedFieldConverter: Option[FromStringOptionDeserializer[A]]
  def multipartFieldConverter: Option[Unmarshaller[A]]
}

object FormFieldConverter extends SingleModeFormFieldsConverters {
  implicit def dualModeFormFieldConverter[A :FromStringOptionDeserializer :Unmarshaller] = new FormFieldConverter[A] {
    val urlEncodedFieldConverter = Some(fromStringOptionDeserializer[A])
    val multipartFieldConverter = Some(unmarshaller[A])
  }
}

sealed abstract class SingleModeFormFieldsConverters {
  implicit def urlEncodedFormFieldConverter[A :FromStringOptionDeserializer] = new FormFieldConverter[A] {
    val urlEncodedFieldConverter = Some(fromStringOptionDeserializer[A])
    def multipartFieldConverter = None
  }

  implicit def multiPartFormFieldConverter[A :Unmarshaller] = new FormFieldConverter[A] {
    def urlEncodedFieldConverter = None
    val multipartFieldConverter = Some(unmarshaller[A])
  }
}










