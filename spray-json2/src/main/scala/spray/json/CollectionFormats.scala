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

package spray.json

import scala.reflect.ClassTag
import collection.generic.CanBuildFrom

/**
 * Supplies the JsonFormats for all types that can be viewed as a Traversable.
 */
trait CollectionFormats {

  /**
    * Supplies the JsonFormat for Arrays.
   */
  implicit def arrayFormat[T :JsonFormat :ClassTag]: RootJsonFormat[Array[T]] = new RootJsonFormat[Array[T]] {
    def write(array: Array[T]) = JsArray(array.map(_.toJson)(collection.breakOut) :Vector[JsValue])
    def read(value: JsValue) = value match {
      case x: JsArray => Validated(x.elements.map(_.as[T])(collection.breakOut) :Array[T])
      case x => deserializationError("Expected Array as JsArray, but got " + x)
    }
  }

  /**
   * Supplies the JsonFormat for Maps. The key type has to be statically known to be convertible to a field name.
   */
  implicit def mapFormatStrict[K :FieldName, V :JsonFormat]: RootJsonFormat[Map[K, V]] = new RootJsonFormat[Map[K, V]] {
    def write(m: Map[K, V]) = JsObject {
      m.map(field => (FieldName.get(field._1), field._2.toJson))
    }
    def read(value: JsValue) = value match {
      case x: JsObject => Validated {
        x.fields.map { field =>
          (FieldName.convert(field._1), field._2.as[V])
        } (collection.breakOut) :Map[K, V]
      }
      case x => deserializationError("Expected Map as JsObject, but got " + x)
    }
  }

  /**
    * Supplies the JsonFormat for Maps. The implicitly available JsonFormat for the key type K must
    * always write JsStrings, otherwise a [[spray.json.SerializationException]] will be thrown.
   */
  def mapFormatNonStrict[K :JsonFormat, V :JsonFormat]: RootJsonFormat[Map[K, V]] = new RootJsonFormat[Map[K, V]] {
    def write(m: Map[K, V]) = JsObject {
      m.map { field =>
        field._1.toJson match {
          case x: JsString => x.value -> field._2.toJson
          case x => throw new SerializationException("Map key must be formatted as JsString, not '" + x + "'")
        }
      }
    }
    def read(value: JsValue) = value match {
      case x: JsObject => Validated {
        x.fields.map { field =>
          (JsString(field._1).as[K], field._2.as[V])
        } (collection.breakOut) :Map[K, V]
      }
      case x => deserializationError("Expected Map as JsObject, but got " + x)
    }
  }

  import collection.{immutable => imm}

  implicit def immIterableFormat[T :JsonFormat]   = traversableFormat[T, imm.Iterable[T]]
  implicit def immSeqFormat[T :JsonFormat]        = traversableFormat[T, imm.Seq[T]]
  implicit def immIndexedSeqFormat[T :JsonFormat] = traversableFormat[T, imm.IndexedSeq[T]]
  implicit def immLinearSeqFormat[T :JsonFormat]  = traversableFormat[T, imm.LinearSeq[T]]
  implicit def immSetFormat[T :JsonFormat]        = traversableFormat[T, imm.Set[T]]
  implicit def vectorFormat[T :JsonFormat]        = traversableFormat[T, Vector[T]]
  implicit def listFormat[T :JsonFormat]          = traversableFormat[T, List[T]]

  import collection._

  implicit def iterableFormat[T :JsonFormat]   = traversableFormat[T, Iterable[T]]
  implicit def seqFormat[T :JsonFormat]        = traversableFormat[T, Seq[T]]
  implicit def indexedSeqFormat[T :JsonFormat] = traversableFormat[T, IndexedSeq[T]]
  implicit def linearSeqFormat[T :JsonFormat]  = traversableFormat[T, LinearSeq[T]]
  implicit def setFormat[T :JsonFormat]        = traversableFormat[T, Set[T]]

  /**
   * Supplies the JsonFormat for all Traversables.
   *
   * (Unfortunately we cannot currently mark this method implicit. If we do the compiler generates all sorts of wierd
   * "diverging implicit" errors that I haven't managed to work around.)
   */
  def traversableFormat[A, T <: Traversable[A]](implicit jfa: JsonFormat[A],
                                                         cbf: CanBuildFrom[T,A,T]): RootJsonFormat[T] = new RootJsonFormat[T] {
    def write(items: T) = JsArray(items.map(_.toJson)(collection.breakOut) :Vector[JsValue])
    def read(value: JsValue) = value match {
      case x: JsArray => Validated(x.elements.map(_.as[A])(collection.breakOut) :T)
      case x => deserializationError("Expected JsArray, but got " + x)
    }
  }

}