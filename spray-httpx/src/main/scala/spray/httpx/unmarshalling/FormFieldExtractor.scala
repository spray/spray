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


sealed trait FormFieldExtractor {
  type Field <: FormField
  def field(name: String): Field
}

object FormFieldExtractor {
  def apply(form: HttpForm): FormFieldExtractor = form match {
    case FormData(fields) => new FormFieldExtractor {
      type Field = UrlEncodedFormField
      def field(name: String) = new UrlEncodedFormField(name, fields.get(name))
    }
    case MultipartFormData(fields) => new FormFieldExtractor {
      type Field = MultipartFormField
      def field(name: String) = new MultipartFormField(name, fields.get(name))
    }
  }
}

sealed abstract class FormField {
  type Raw
  def name: String
  def rawValue: Option[Raw]
  def exists = rawValue.isDefined
  def as[T :FormFieldConverter]: Deserialized[T]

  protected def fail(fieldName: String, expected: String) =
    Left(UnsupportedContentType("Field '%s' can only be read from '%s' form content".format(fieldName, expected)))
}

class UrlEncodedFormField(val name: String, val rawValue: Option[String]) extends FormField {
  type Raw = String
  def as[T](implicit ffc: FormFieldConverter[T]) = ffc.urlEncodedFieldConverter match {
    case Some(conv) => conv(rawValue)
    case None => fail(name, "multipart/form-data")
  }
}

class MultipartFormField(val name: String, val rawValue: Option[BodyPart]) extends FormField {
  type Raw = BodyPart
  def as[T](implicit ffc: FormFieldConverter[T]) = ffc.multipartFieldConverter match {
    case Some(conv) => conv(rawValue.map(_.entity))
    case None => fail(name, "application/x-www-form-urlencoded")
  }
}


import spray.httpx.unmarshalling.{FromStringOptionDeserializer => FSOD, FromEntityOptionUnmarshaller => FEOU}

sealed abstract class FormFieldConverter[T] { self =>
  def urlEncodedFieldConverter: Option[FSOD[T]]
  def multipartFieldConverter: Option[FEOU[T]]
  def withDefault(default: T): FormFieldConverter[T] =
    new FormFieldConverter[T] {
      lazy val urlEncodedFieldConverter = self.urlEncodedFieldConverter.map(_.withDefaultValue(default))
      lazy val multipartFieldConverter = self.multipartFieldConverter.map(_.withDefaultValue(default))
    }
}

object FormFieldConverter extends FfcLowerPrioImplicits {
  implicit def dualModeFormFieldConverter[T :FSOD :FEOU] = new FormFieldConverter[T] {
    lazy val urlEncodedFieldConverter = Some(implicitly[FSOD[T]])
    lazy val multipartFieldConverter = Some(implicitly[FEOU[T]])
  }
  def fromFSOD[T](fsod: FSOD[T])(implicit feou: FEOU[T] = null) =
    if (feou == null) urlEncodedFormFieldConverter(fsod) else dualModeFormFieldConverter(fsod, feou)
}

private[unmarshalling] abstract class FfcLowerPrioImplicits extends FfcLowerPrioImplicits2 {
  implicit def urlEncodedFormFieldConverter[T :FSOD] = new FormFieldConverter[T] {
    lazy val urlEncodedFieldConverter = Some(implicitly[FSOD[T]])
    def multipartFieldConverter = None
  }

  implicit def multiPartFormFieldConverter[T :FEOU] = new FormFieldConverter[T] {
    def urlEncodedFieldConverter = None
    lazy val multipartFieldConverter = Some(implicitly[FEOU[T]])
  }
}

private[unmarshalling] abstract class FfcLowerPrioImplicits2 {
  implicit def liftToTargetOption[T](implicit ffc: FormFieldConverter[T]) = new FormFieldConverter[Option[T]] {
    lazy val urlEncodedFieldConverter = ffc.urlEncodedFieldConverter.map(Deserializer.liftToTargetOption(_))
    lazy val multipartFieldConverter = ffc.multipartFieldConverter.map(Deserializer.liftToTargetOption(_))
  }
}