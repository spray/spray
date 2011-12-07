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

import scala.{Left, Right}

/**
  * Provides the JsonFormats for the non-collection standard types.
 */
trait StandardFormats {
  this: AdditionalFormats =>

  private[json] type JF[T] = JsonFormat[T] // simple alias for reduced verbosity

  implicit def optionFormat[T :JF] = new OptionFormat[T]

  class OptionFormat[T :JF] extends JF[Option[T]] {
    def write(option: Option[T]) = option match {
      case Some(x) => x.toJson
      case None => JsNull
    }
    def read(value: JsValue) = value match {
      case JsNull => None
      case x => Some(x.convertTo[T])
    }
  }

  implicit def eitherFormat[A :JF, B :JF] = new JF[Either[A, B]] {
    def write(either: Either[A, B]) = either match {
      case Right(a) => a.toJson
      case Left(b) => b.toJson
    }
    def read(value: JsValue) = (value.convertTo(safeReader[A]), value.convertTo(safeReader[B])) match {
      case (Right(a), _: Left[_, _]) => Left(a)
      case (_: Left[_, _], Right(b)) => Right(b)
      case (_: Right[_, _], _: Right[_, _]) => deserializationError("Ambiguous Either value: can be read as both, Left and Right, values")
      case (Left(ea), Left(eb)) => deserializationError("Could not read Either value:\n" + ea + "---------- and ----------\n" + eb)
    }
  }
  
  implicit def tuple1Format[A :JF] = new JF[Tuple1[A]] {
    def write(t: Tuple1[A]) = t._1.toJson
    def read(value: JsValue) = Tuple1(value.convertTo[A])
  }
  
  implicit def tuple2Format[A :JF, B :JF] = new RootJsonFormat[(A, B)] {
    def write(t: (A, B)) = JsArray(t._1.toJson, t._2.toJson)
    def read(value: JsValue) = value match {
      case JsArray(a :: b :: Nil) => (a.convertTo[A], b.convertTo[B])
      case x => deserializationError("Expected Tuple2 as JsArray, but got " + x)
    }
  }
  
  implicit def tuple3Format[A :JF, B :JF, C :JF] = new RootJsonFormat[(A, B, C)] {
    def write(t: (A, B, C)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson)
    def read(value: JsValue) = value match {
      case JsArray(a :: b :: c :: Nil) => (a.convertTo[A], b.convertTo[B], c.convertTo[C])
      case x => deserializationError("Expected Tuple3 as JsArray, but got " + x)
    }
  }
  
  implicit def tuple4Format[A :JF, B :JF, C :JF, D :JF] = new RootJsonFormat[(A, B, C, D)] {
    def write(t: (A, B, C, D)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson)
    def read(value: JsValue) = value match {
      case JsArray(a :: b :: c :: d :: Nil) => (a.convertTo[A], b.convertTo[B], c.convertTo[C], d.convertTo[D])
      case x => deserializationError("Expected Tuple4 as JsArray, but got " + x)
    }
  }
  
  implicit def tuple5Format[A :JF, B :JF, C :JF, D :JF, E :JF] = {
    new RootJsonFormat[(A, B, C, D, E)] {
      def write(t: (A, B, C, D, E)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson, t._5.toJson)
      def read(value: JsValue) = value match {
        case JsArray(a :: b :: c :: d :: e :: Nil) =>
          (a.convertTo[A], b.convertTo[B], c.convertTo[C], d.convertTo[D], e.convertTo[E])
        case x => deserializationError("Expected Tuple5 as JsArray, but got " + x)
      }
    }
  }
  
  implicit def tuple6Format[A :JF, B :JF, C :JF, D :JF, E :JF, F: JF] = {
    new RootJsonFormat[(A, B, C, D, E, F)] {
      def write(t: (A, B, C, D, E, F)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson, t._5.toJson, t._6.toJson)
      def read(value: JsValue) = value match {
        case JsArray(a :: b :: c :: d :: e :: f :: Nil) =>
          (a.convertTo[A], b.convertTo[B], c.convertTo[C], d.convertTo[D], e.convertTo[E], f.convertTo[F])
        case x => deserializationError("Expected Tuple6 as JsArray, but got " + x)
      }
    }
  }
  
  implicit def tuple7Format[A :JF, B :JF, C :JF, D :JF, E :JF, F: JF, G: JF] = {
    new RootJsonFormat[(A, B, C, D, E, F, G)] {
      def write(t: (A, B, C, D, E, F, G)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson, t._5.toJson, t._6.toJson, t._6.toJson)
      def read(value: JsValue) = value match {
        case JsArray(a :: b :: c :: d :: e :: f :: g :: Nil) =>
          (a.convertTo[A], b.convertTo[B], c.convertTo[C], d.convertTo[D], e.convertTo[E], f.convertTo[F], g.convertTo[G])
        case x => deserializationError("Expected Tuple7 as JsArray, but got " + x)
      }
    }
  }
  
}