/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.httpx.unmarshalling

import cc.spray.http._


sealed trait FormFieldExtractor {
  def field(name: String): Deserialized[FormField]
}

object FormFieldExtractor {
  private def fail(fieldName: String, expected: String) =
    Left(UnsupportedContentType("Field '%s' can only be read from '%s' form content".format(fieldName, expected)))
  private def fail(fieldName: String) =
    Left(MalformedContent("Form data do not contain a field with name '" + fieldName + "'"))

  def apply(form: HttpForm): FormFieldExtractor = form match {
    case FormData(fields) => new FormFieldExtractor {
      def field(name: String) = fields.get(name) match {
        case Some(value) => Right {
          new UrlEncodedFormField {
            def raw = value
            def as[T](implicit ffc: FormFieldConverter[T]) = ffc.urlEncodedFieldConverter match {
              case Some(conv) => conv(value)
              case None => fail(name, "multipart/form-data")
            }
          }
        }
        case None => fail(name)
      }
    }

    case MultipartFormData(fields) => new FormFieldExtractor {
      def field(name: String) = fields.get(name) match {
        case Some(value) => Right {
          new MultipartFormField {
            def raw = value
            def as[T](implicit ffc: FormFieldConverter[T]) = ffc.multipartFieldConverter match {
              case Some(conv) => conv(value.entity)
              case None => fail(name, "application/x-www-form-urlencoded")
            }
          }
        }
        case None => fail(name)
      }
    }
  }
}

sealed trait FormField {
  def as[T :FormFieldConverter]: Deserialized[T]
}

trait UrlEncodedFormField extends FormField {
  def raw: String
}

trait MultipartFormField extends FormField {
  def raw: BodyPart
}

sealed trait FormFieldConverter[T] {
  def urlEncodedFieldConverter: Option[FromStringDeserializer[T]]
  def multipartFieldConverter: Option[Unmarshaller[T]]
}

object FormFieldConverter extends FormFieldConverterLowerPriorityImplicits {
  implicit def dualModeFormFieldConverter[T :FromStringDeserializer :Unmarshaller] = new FormFieldConverter[T] {
    lazy val urlEncodedFieldConverter = Some(implicitly[FromStringDeserializer[T]])
    lazy val multipartFieldConverter = Some(implicitly[Unmarshaller[T]])
  }
}

private[unmarshalling] abstract class FormFieldConverterLowerPriorityImplicits
  extends FormFieldConverterLowerPriorityImplicits2 {
  implicit def urlEncodedFormFieldConverter[T :FromStringDeserializer] = new FormFieldConverter[T] {
    lazy val urlEncodedFieldConverter = Some(implicitly[FromStringDeserializer[T]])
    def multipartFieldConverter = None
  }

  implicit def multiPartFormFieldConverter[T :Unmarshaller] = new FormFieldConverter[T] {
    def urlEncodedFieldConverter = None
    lazy val multipartFieldConverter = Some(implicitly[Unmarshaller[T]])
  }
}

private[unmarshalling] abstract class FormFieldConverterLowerPriorityImplicits2 {
  implicit def liftToTargetOption[T](implicit ffc: FormFieldConverter[T]) = {
    new FormFieldConverter[Option[T]] {
      lazy val urlEncodedFieldConverter = ffc.urlEncodedFieldConverter.map(Deserializer.liftToTargetOption(_))
      lazy val multipartFieldConverter = ffc.multipartFieldConverter.map(Deserializer.liftToTargetOption(_))
    }
  }
}