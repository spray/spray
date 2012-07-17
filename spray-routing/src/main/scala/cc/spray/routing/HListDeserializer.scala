/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.routing

import cc.spray.httpx.unmarshalling.{MalformedContent, DeserializationError, Deserializer}
import shapeless._


trait HListDeserializer[L <: HList, T] extends Deserializer[L, T]

object HListDeserializer {

  private type DS[A, AA] = Deserializer[A, AA] // alias for brevity

  implicit def fromDeserializer[L <: HList, T](ds: DS[L, T]) = new HListDeserializer[L, T] {
    def apply(list: L) = ds(list)
  }

  /////////////////////////////// CASE CLASS DESERIALIZATION ////////////////////////////////

  // we use a special exception to bubble up errors rather than relying on long "right.flatMap" cascades in order to
  // save lines of code as well as excessive closure class creation in the many "hld" methods below
  private class BubbleLeftException(val left: Left[Any, Any]) extends RuntimeException

  private def create[L <: HList, T](deserialize: L => T) = new HListDeserializer[L, T] {
    def apply(list: L) = {
      try Right(deserialize(list))
      catch {
        case e: BubbleLeftException => e.left.asInstanceOf[Left[DeserializationError, T]]
        case e: IllegalArgumentException => Left(MalformedContent(e.getMessage))
      }
    }
  }

  private def get[T](either: Either[DeserializationError, T]): T = either match {
    case Right(x) => x
    case left: Left[_, _] => throw new BubbleLeftException(left)
  }

  implicit def hld1[T <: Product, A, AA]
      (construct: AA => T)
      (implicit qa: DS[A, AA]) =
    create[A :: HNil, T] {
      case a :: HNil => construct(
        get(qa(a))
      )
    }

  implicit def hld2[T <: Product, A, AA, B, BB]
      (construct: (AA, BB) => T)
      (implicit qa: DS[A, AA], qb: DS[B, BB]) =
    create[A :: B :: HNil, T] {
      case a :: b :: HNil => construct(
        get(qa(a)),
        get(qb(b))
      )
    }

  implicit def hld3[T <: Product, A, AA, B, BB, C, CC]
      (construct: (AA, BB, CC) => T)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC]) =
    create[A :: B :: C :: HNil, T] {
      case a :: b :: c :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c))
      )
    }

}