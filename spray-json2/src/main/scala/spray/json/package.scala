/*
 * Copyright (C) 2009-2011 Mathias Doenitz
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

package spray

package object json extends PackageImplicits {

  type JsField = (String, JsValue)

  def deserializationError[T](msg: String, cause: Throwable = null): Validated[T] =
    Failure(new DeserializationException(msg, cause))
  def serializationError(msg: String) =
    throw new SerializationException(msg)

  def jsonFormat[T](implicit format: JsonFormat[T]) = format
  def jsonReader[T](implicit reader: JsonReader[T]) = reader
  def jsonWriter[T](implicit writer: JsonWriter[T]) = writer

  implicit def richAny[T](any: T) = new RichAny(any)
  implicit def richString(string: String) = new RichString(string)

  // really?
  implicit def enrichFuncWithFromJsonConversion[A: JsonFormat, B](f: A ⇒ B): JsValue ⇒ B =
    json ⇒ f(json.as[A])

}

package json {

  class DeserializationException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)
  class SerializationException(msg: String) extends RuntimeException(msg)

  private[json] class RichAny[T](any: T) {
    def toJson(implicit writer: JsonWriter[T]): JsValue = writer.write(any)
  }

  private[json] class RichString(string: String) {
    def asJson: JsValue = JsonParser(string)
  }
}
