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

import annotation.implicitNotFound

/**
  * Provides the JSON deserialization for type T.
 */
@implicitNotFound(msg = "Cannot find JsonReader or JsonFormat type class for ${T}")
trait JsonReader[T] {
  def read(json: JsValue): T
}

object JsonReader {
  implicit def func2Reader[T](f: JsValue => T): JsonReader[T] = new JsonReader[T] {
    def read(json: JsValue) = f(json)
  }
}

/**
  * Provides the JSON serialization for type T.
 */
@implicitNotFound(msg = "Cannot find JsonWriter or JsonFormat type class for ${T}")
trait JsonWriter[T] {
  def write(obj: T): JsValue
}

object JsonWriter {
  implicit def func2Writer[T](f: T => JsValue): JsonWriter[T] = new JsonWriter[T] {
    def write(obj: T) = f(obj)
  }
}

/**
  * Provides the JSON deserialization and serialization for type T.
 */
trait JsonFormat[T] extends JsonReader[T] with JsonWriter[T]
