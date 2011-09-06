/*
 * Original implementation (C) 2009-2011 Debasish Ghosh
 * Adapted and extended in 2011 by Mathias Doenitz
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
  * Provides additional JsonFormats and helpers
 */
trait AdditionalFormats {

  implicit object JsValueFormat extends JsonFormat[JsValue] {
    def write(value: JsValue) = value
    def read(value: JsValue) = value
  }

  def jsonFormat[T](reader: JsonReader[T], writer: JsonWriter[T]) = new JsonFormat[T] {
    def write(obj: T) = writer.write(obj)
    def read(json: JsValue) = reader.read(json)
  }

  def lift[T](writer :JsonWriter[T]) = new JsonFormat[T] {
    def write(obj: T): JsValue = writer.write(obj)
    def read(value: JsValue) =
      throw new UnsupportedOperationException("JsonReader implementation missing")
  }

  def lift[T <: AnyRef](reader :JsonReader[T]) = new JsonFormat[T] {
    def write(obj: T): JsValue =
      throw new UnsupportedOperationException("No JsonWriter[" + obj.getClass + "] available")
    def read(value: JsValue) = reader.read(value)
  }

  /**
   * Lazy wrapper around serialization. Useful when you want to serialize (mutually) recursive structures.
   */
  def lazyFormat[T](format: => JsonFormat[T]) = new JsonFormat[T]{
    lazy val delegate = format;
    def write(x: T) = delegate.write(x);
    def read(value: JsValue) = delegate.read(value);
  }

  /**
   * Wraps an existing JsonReader with Exception protection.
   */
  def safeReader[A :JsonReader] = new JsonReader[Either[Exception, A]] {
    def read(json: JsValue) = {
      try {
        Right(json.fromJson)
      } catch {
        case e: Exception => Left(e)
      }
    }
  }

}