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

import scala.{Left, Right}

/**
  * Provides the JsonFormats for the non-collection standard types.
 */
trait StandardFormats extends LowLevelStandardFormats {

  protected type JF[T] = JsonFormat[T] // simple alias for reduced verbosity

  implicit def eitherFormat[A :JF, B :JF]: JF[Either[A, B]] = new JF[Either[A, B]] {
    def write(either: Either[A, B]) = either match {
      case Right(a) => a.toJson
      case Left(b) => b.toJson
    }
    def read(value: JsValue) = (value.toValidated[A], value.toValidated[B]) match {
      case (Success(a), _: Failure[_]) => Success(Left(a))
      case (_: Failure[_], Success(b)) => Success(Right(b))
      case (_: Success[_], _: Success[_]) => deserializationError("Ambiguous Either value: can be read as both, Left and Right, values")
      case (Failure(ea), Failure(eb)) => deserializationError("Could not read Either value:\n" + ea + "\n---------- and ----------\n" + eb)
    }
  }

  implicit def tuple2AsJsObject[F: FieldName, A :JF]: RootJsonFormat[(F, A)] = new RootJsonFormat[(F, A)] {
    def write(obj: (F, A)): JsValue =
      JsObject(FieldName.get(obj._1) -> obj._2.toJson)

    def read(json: JsValue): Validated[(F, A)] = json match {
      case JsObject(fields) if fields.size == 1 =>
        val (key, value) = fields.head
        Validated((FieldName.convert(key), value.as[A]))
      case x => deserializationError("Expected Tuple2(String, X) as JsObject, but got " + x)
    }
  }

  implicit def tuple1Format[A :JF]: JF[Tuple1[A]] = new JF[Tuple1[A]] {
    def write(t: Tuple1[A]) = t._1.toJson
    def read(value: JsValue) = value.toValidated[A].map(Tuple1(_))
  }

  implicit def tuple3Format[A :JF, B :JF, C :JF]: RootJsonFormat[(A, B, C)] = new RootJsonFormat[(A, B, C)] {
    def write(t: (A, B, C)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson)
    def read(value: JsValue) = value match {
      case JsArray(Seq(a, b, c)) => Validated((a.as[A], b.as[B], c.as[C]))
      case x => deserializationError("Expected Tuple3 as JsArray, but got " + x)
    }
  }

  implicit def tuple4Format[A :JF, B :JF, C :JF, D :JF]: RootJsonFormat[(A, B, C, D)] = new RootJsonFormat[(A, B, C, D)] {
    def write(t: (A, B, C, D)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson)
    def read(value: JsValue) = value match {
      case JsArray(Seq(a, b, c, d)) => Validated((a.as[A], b.as[B], c.as[C], d.as[D]))
      case x => deserializationError("Expected Tuple4 as JsArray, but got " + x)
    }
  }

  implicit def tuple5Format[A :JF, B :JF, C :JF, D :JF, E :JF]: RootJsonFormat[(A, B, C, D, E)] = {
    new RootJsonFormat[(A, B, C, D, E)] {
      def write(t: (A, B, C, D, E)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson, t._5.toJson)
      def read(value: JsValue) = value match {
        case JsArray(Seq(a, b, c, d, e)) =>
          Validated((a.as[A], b.as[B], c.as[C], d.as[D], e.as[E]))
        case x => deserializationError("Expected Tuple5 as JsArray, but got " + x)
      }
    }
  }

  implicit def tuple6Format[A :JF, B :JF, C :JF, D :JF, E :JF, F: JF]: RootJsonFormat[(A, B, C, D, E, F)] = {
    new RootJsonFormat[(A, B, C, D, E, F)] {
      def write(t: (A, B, C, D, E, F)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson, t._5.toJson, t._6.toJson)
      def read(value: JsValue) = value match {
        case JsArray(Seq(a, b, c, d, e, f)) =>
          Validated((a.as[A], b.as[B], c.as[C], d.as[D], e.as[E], f.as[F]))
        case x => deserializationError("Expected Tuple6 as JsArray, but got " + x)
      }
    }
  }

  implicit def tuple7Format[A :JF, B :JF, C :JF, D :JF, E :JF, F: JF, G: JF]: RootJsonFormat[(A, B, C, D, E, F, G)] = {
    new RootJsonFormat[(A, B, C, D, E, F, G)] {
      def write(t: (A, B, C, D, E, F, G)) = JsArray(t._1.toJson, t._2.toJson, t._3.toJson, t._4.toJson, t._5.toJson, t._6.toJson, t._6.toJson)
      def read(value: JsValue) = value match {
        case JsArray(Seq(a, b, c, d, e, f, g)) =>
          Validated((a.as[A], b.as[B], c.as[C], d.as[D], e.as[E], f.as[F], g.as[G]))
        case x => deserializationError("Expected Tuple7 as JsArray, but got " + x)
      }
    }
  }

}

trait LowLevelStandardFormats { self: StandardFormats =>
  implicit def tuple2Format[A :JF, B :JF]: RootJsonFormat[(A, B)] = new RootJsonFormat[(A, B)] {
    def write(t: (A, B)) = JsArray(t._1.toJson, t._2.toJson)
    def read(value: JsValue) = value match {
      case JsArray(Seq(a, b)) => Validated((a.as[A], b.as[B]))
      case x => deserializationError("Expected Tuple2 as JsArray, but got " + x)
    }
  }
}
