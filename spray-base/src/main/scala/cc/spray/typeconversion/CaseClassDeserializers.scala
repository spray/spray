/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.typeconversion

trait CaseClassDeserializers {

  private type DS[AA, A] = Deserializer[AA, A]

  implicit def caseClassDeserializer1[T <: Product, A, AA]
      (construct: A => T)
      (implicit qa: DS[AA, A]) = {
    new ProductDeserializer[Tuple1[AA], T] {
      def deserialize(s: Tuple1[AA]) = construct(
        get(qa(s._1))
      )
    }
  }

  implicit def caseClassDeserializer2[T <: Product, A, AA, B, BB]
      (construct: (A, B) => T)
      (implicit qa: DS[AA, A], qb: DS[BB, B]) = {
    new ProductDeserializer[(AA, BB), T] {
      def deserialize(s: (AA, BB)) = construct(
        get(qa(s._1)),
        get(qb(s._2))
      )
    }
  }

  implicit def caseClassDeserializer3[T <: Product, A, AA, B, BB, C, CC]
      (construct: (A, B, C) => T)
      (implicit qa: DS[AA, A], qb: DS[BB, B], qc: DS[CC, C]) = {
    new ProductDeserializer[(AA, BB, CC), T] {
      def deserialize(s: (AA, BB, CC)) = construct(
        get(qa(s._1)),
        get(qb(s._2)),
        get(qc(s._3))
      )
    }
  }

  implicit def caseClassDeserializer4[T <: Product, A, AA, B, BB, C, CC, D, DD]
      (construct: (A, B, C, D) => T)
      (implicit qa: DS[AA, A], qb: DS[BB, B], qc: DS[CC, C], qd: DS[DD, D]) = {
    new ProductDeserializer[(AA, BB, CC, DD), T] {
      def deserialize(s: (AA, BB, CC, DD)) = construct(
        get(qa(s._1)),
        get(qb(s._2)),
        get(qc(s._3)),
        get(qd(s._4))
      )
    }
  }

  implicit def caseClassDeserializer5[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE]
      (construct: (A, B, C, D, E) => T)
      (implicit qa: DS[AA, A], qb: DS[BB, B], qc: DS[CC, C], qd: DS[DD, D], qe: DS[EE, E]) = {
    new ProductDeserializer[(AA, BB, CC, DD, EE), T] {
      def deserialize(s: (AA, BB, CC, DD, EE)) = construct(
        get(qa(s._1)),
        get(qb(s._2)),
        get(qc(s._3)),
        get(qd(s._4)),
        get(qe(s._5))
      )
    }
  }

  implicit def caseClassDeserializer6[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF]
      (construct: (A, B, C, D, E, F) => T)
      (implicit qa: DS[AA, A], qb: DS[BB, B], qc: DS[CC, C], qd: DS[DD, D], qe: DS[EE, E], qf: DS[FF, F]) = {
    new ProductDeserializer[(AA, BB, CC, DD, EE, FF), T] {
      def deserialize(s: (AA, BB, CC, DD, EE, FF)) = construct(
        get(qa(s._1)),
        get(qb(s._2)),
        get(qc(s._3)),
        get(qd(s._4)),
        get(qe(s._5)),
        get(qf(s._6))
      )
    }
  }

  implicit def caseClassDeserializer7[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG]
      (construct: (A, B, C, D, E, F, G) => T)
      (implicit qa: DS[AA, A], qb: DS[BB, B], qc: DS[CC, C], qd: DS[DD, D], qe: DS[EE, E], qf: DS[FF, F], qg: DS[GG, G]) = {
    new ProductDeserializer[(AA, BB, CC, DD, EE, FF, GG), T] {
      def deserialize(s: (AA, BB, CC, DD, EE, FF, GG)) = construct(
        get(qa(s._1)),
        get(qb(s._2)),
        get(qc(s._3)),
        get(qd(s._4)),
        get(qe(s._5)),
        get(qf(s._6)),
        get(qg(s._7))
      )
    }
  }

  implicit def caseClassDeserializer8[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH]
      (construct: (A, B, C, D, E, F, G, H) => T)
      (implicit qa: DS[AA, A], qb: DS[BB, B], qc: DS[CC, C], qd: DS[DD, D], qe: DS[EE, E], qf: DS[FF, F], qg: DS[GG, G], qh: DS[HH, H]) = {
    new ProductDeserializer[(AA, BB, CC, DD, EE, FF, GG, HH), T] {
      def deserialize(s: (AA, BB, CC, DD, EE, FF, GG, HH)) = construct(
        get(qa(s._1)),
        get(qb(s._2)),
        get(qc(s._3)),
        get(qd(s._4)),
        get(qe(s._5)),
        get(qf(s._6)),
        get(qg(s._7)),
        get(qh(s._8))
      )
    }
  }

  implicit def caseClassDeserializer9[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II]
      (construct: (A, B, C, D, E, F, G, H, I) => T)
      (implicit qa: DS[AA, A], qb: DS[BB, B], qc: DS[CC, C], qd: DS[DD, D], qe: DS[EE, E], qf: DS[FF, F], qg: DS[GG, G], qh: DS[HH, H], qi: DS[II, I]) = {
    new ProductDeserializer[(AA, BB, CC, DD, EE, FF, GG, HH, II), T] {
      def deserialize(s: (AA, BB, CC, DD, EE, FF, GG, HH, II)) = construct(
        get(qa(s._1)),
        get(qb(s._2)),
        get(qc(s._3)),
        get(qd(s._4)),
        get(qe(s._5)),
        get(qf(s._6)),
        get(qg(s._7)),
        get(qh(s._8)),
        get(qi(s._9))
      )
    }
  }

}

abstract class ProductDeserializer[S, T] extends Deserializer[S, T] {
  // we use a special exception to bubble up errors rather than relying on long "right.flatMap" cascades in order
  // to save lines of code as well as excessive closure class creation in the many "caseClassDeserializer" methods above
  protected class BubbleLeftException(val left: Left[DeserializationError, T]) extends RuntimeException

  def apply(s: S) = {
    try {
      Right(deserialize(s))
    } catch {
      case e: BubbleLeftException => e.left
      case e: IllegalArgumentException => Left(MalformedContent(e.getMessage))
    }
  }

  protected def get[A](either: Either[DeserializationError, A]): A = either match {
    case Right(a) => a
    case left: Left[_, _] => throw new BubbleLeftException(left.asInstanceOf[Left[DeserializationError, T]])
  }

  protected def deserialize(s: S): T
}








