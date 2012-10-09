/*
 * Copyright (C) 2012 Mathias Doenitz, Johannes Rudolph
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
package cc.spray.json

/**
 * Defines how a value can be un/marshalled into fields of a json object. This is
 * similar to a JsonFormat but works on a different level: it is not possible to
 * un/marshal arbitrary JsValues but only fields of a JsObject.
 */
trait ObjectFieldFormat[T] { outer =>
  def fromFields(fields: Map[String, JsValue]): Validated[T]
  def fromValue(t: T): Seq[(String, JsValue)]

  def withDefault(defaultT: T): ObjectFieldFormat[T] = new ObjectFieldFormat[T] {
    def fromFields(fields: Map[String, JsValue]): Validated[T] =
      outer.fromFields(fields).orElse(Success(defaultT))

    def fromValue(t: T): Seq[(String, JsValue)] =
      outer.fromValue(t)
  }

  def withMissingFieldAt(nullValue: T): ObjectFieldFormat[T] = new ObjectFieldFormat[T] {
    def fromFields(fields: Map[String, JsValue]): Validated[T] =
      outer.fromFields(fields).orElse(Success(nullValue))

    def fromValue(t: T): Seq[(String, JsValue)] = {
      if (t == nullValue)
        Nil
      else
        outer.fromValue(t)
    }
  }
}
object ObjectFieldFormat {
  implicit def liftFieldName[T](_fieldName: String)(implicit extra: ExtraFormat[T], tF: JsonFormat[T]): ObjectFieldFormat[T] =
    extra(apply(_fieldName))

  /**
   * The most basic CaseClassFieldFormat which maps a value directly to
   * one field of a json object.
   */
  def apply[T: JsonFormat](fieldName: String) =
    new ObjectFieldFormat[T] {
      def fromFields(fields: Map[String, JsValue]): Validated[T] =
        if (fields.contains(fieldName))
          fields(fieldName).toValidated[T]
        else
          deserializationError("JsObject is missing required member '" + fieldName + "'")

      def fromValue(t: T): Seq[(String, JsValue)] = fieldName -> t.toJson :: Nil
    }
}

trait ObjectFieldFormatBuilderImplicits {
  case class RichFieldName(fieldName: String) {
    def as[T: JsonFormat]: FieldProducer[T] = FieldProducer[T](fieldName)
    def optionAsNull[T](implicit jf: JsonFormat[T], conv: Option[Nothing] <:< T): ObjectFieldFormat[T] =
      ObjectFieldFormat[T](fieldName)
  }
  case class FieldProducer[T: JsonFormat](fieldName: String) {
    def using[U](f1: T => U, f2: U => T): ObjectFieldFormat[U] = ObjectFieldFormat(fieldName)(JsonFormat.get[T].map(f1, f2))
  }
  implicit def toRichFieldName(fieldName: String): RichFieldName = RichFieldName(fieldName)

  // we include that here as well, so that CCFF's methods can be used directly on the
  // field names
  implicit def liftFieldName[T: ExtraFormat: JsonFormat](fieldName: String) =
    ObjectFieldFormat.liftFieldName(fieldName)

  def ignore[T](defaultT: T): ObjectFieldFormat[T] = new ObjectFieldFormat[T] {
    def fromFields(fields: Map[String, JsValue]): Validated[T] =
      Success(defaultT)

    def fromValue(t: T): Seq[(String, JsValue)] =
      Nil
  }
}

/**
 * Defines extra behavior which can be added to CaseClassFieldFormats based on type.
 * Is currently only used to define the default behavior of options which are usually
 * represented as missing fields (and not with their default option format).
 */
trait ExtraFormat[T] extends (ObjectFieldFormat[T] => ObjectFieldFormat[T])
trait LowLevelExtra {
  implicit def simple[T]: ExtraFormat[T] =
    new ExtraFormat[T] {
      def apply(v1: ObjectFieldFormat[T]): ObjectFieldFormat[T] = v1
    }
}
object ExtraFormat extends LowLevelExtra {
  implicit def removeNone[T](implicit conv: Option[Nothing] <:< T): ExtraFormat[T] =
    new ExtraFormat[T] {
      def apply(v1: ObjectFieldFormat[T]): ObjectFieldFormat[T] = v1.withMissingFieldAt(conv(None))
    }
}