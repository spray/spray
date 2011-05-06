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
package formats

/**
  * Provides the JsonFormats for the non-collection standard types.
 */
trait StandardFormats {

  private type JF[T] = JsonFormat[T] // simple alias for reduced verbosity
  
  implicit def optionFormat[T :JF] = new JF[Option[T]] {
    def write(option: Option[T]) = option match {
      case Some(x) => x.toJson
      case None => JsNull
    }
    def read(value: JsValue) = value match {
      case JsNull => None
      case x => Some(x.fromJson)
    }
  }
  
  implicit def tuple1Format[A :JF] = new JF[Tuple1[A]] {
    def write(t: Tuple1[A]) = t._1.toJson
    def read(value: JsValue) = Tuple1(value.fromJson[A])
  }
  
  implicit def tuple2Format[A :JF, B :JF] = new JF[(A, B)] {
    def write(t: (A, B)) = JsArray(t._1.toJson, t._2.toJson)
    def read(value: JsValue) = value match {
      case JsArray(a :: b :: Nil) => (a.fromJson[A], b.fromJson[B])
      case _ => throw new DeserializationException("Tuple2 expected")
    }
  }
  
  implicit def tuple3Format[A :JF, B :JF, C :JF] = new JF[(A, B, C)] {
    def write(t: (A, B, C)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson)
    def read(value: JsValue) = value match {
      case JsArray(a :: b :: c :: Nil) => (a.fromJson[A], b.fromJson[B], c.fromJson[C])
      case _ => throw new DeserializationException("Tuple3 expected")
    }
  }
  
  implicit def tuple4Format[A :JF, B :JF, C :JF, D :JF] = new JF[(A, B, C, D)] {
    def write(t: (A, B, C, D)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson)
    def read(value: JsValue) = value match {
      case JsArray(a :: b :: c :: d :: Nil) => (a.fromJson[A], b.fromJson[B], c.fromJson[C], d.fromJson[D])
      case _ => throw new DeserializationException("Tuple4 expected")
    }
  }
  
  implicit def tuple5Format[A :JF, B :JF, C :JF, D :JF, E :JF] = {
    new JF[(A, B, C, D, E)] {
      def write(t: (A, B, C, D, E)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson, t._5.toJson)
      def read(value: JsValue) = value match {
        case JsArray(a :: b :: c :: d :: e :: Nil) => {
          (a.fromJson[A], b.fromJson[B], c.fromJson[C], d.fromJson[D], e.fromJson[E])
        }
        case _ => throw new DeserializationException("Tuple5 expected")
      }
    }
  }
  
  implicit def tuple6Format[A :JF, B :JF, C :JF, D :JF, E :JF, F: JF] = {
    new JF[(A, B, C, D, E, F)] {
      def write(t: (A, B, C, D, E, F)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson, t._5.toJson, t._6.toJson)
      def read(value: JsValue) = value match {
        case JsArray(a :: b :: c :: d :: e :: f :: Nil) => {
          (a.fromJson[A], b.fromJson[B], c.fromJson[C], d.fromJson[D], e.fromJson[E], f.fromJson[F])
        }
        case _ => throw new DeserializationException("Tuple6 expected")
      }
    }
  }
  
  implicit def tuple7Format[A :JF, B :JF, C :JF, D :JF, E :JF, F: JF, G: JF] = {
    new JF[(A, B, C, D, E, F, G)] {
      def write(t: (A, B, C, D, E, F, G)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson, t._5.toJson, t._6.toJson, t._6.toJson)
      def read(value: JsValue) = value match {
        case JsArray(a :: b :: c :: d :: e :: f :: g :: Nil) => {
          (a.fromJson[A], b.fromJson[B], c.fromJson[C], d.fromJson[D], e.fromJson[E], f.fromJson[F], g.fromJson[G])
        }
        case _ => throw new DeserializationException("Tuple7 expected")
      }
    }
  }
  
}