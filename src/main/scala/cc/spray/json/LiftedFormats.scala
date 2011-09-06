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

trait LiftedFormats {
  implicit def liftJsonWriter[T](implicit writer: JsonWriter[T]): JsonFormat[T] = new JsonFormat[T] {
    def write(obj: T): JsValue = writer.write(obj)
    def read(value: JsValue) = throw new UnsupportedOperationException("No reader available")
  }

  implicit def liftJsonReader[T](implicit reader: JsonReader[T]): JsonFormat[T] = new JsonFormat[T] {
    def write(obj: T): JsValue = throw new UnsupportedOperationException("No writer available")
    def read(value: JsValue) = reader.read(value)
  }
}

object LiftedFormats extends LiftedFormats