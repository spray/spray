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

import annotation.tailrec

/**
 * Provides the helpers for constructing custom JsonFormat implementations for types implementing the Product trait
 * (especially case classes)
 */
trait ProductFormats {
  this: StandardFormats =>

  def jsonFormat[A :JF, T <: Product](construct: A => T, a: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0)
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a)
    )
  }

  def jsonFormat[A :JF, B :JF, T <: Product](construct: (A, B) => T, a: String, b: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, T <: Product](construct: (A, B, C) => T,
                                                a: String, b: String, c: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1,
      toField[C](c, p, 2)))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, T <: Product](construct: (A, B, C, D) => T,
                                                       a: String, b: String, c: String, d: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1,
      toField[C](c, p, 2,
      toField[D](d, p, 3))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, T <: Product](construct: (A, B, C, D, E) => T,
        a: String, b: String, c: String, d: String, e: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1,
      toField[C](c, p, 2,
      toField[D](d, p, 3,
      toField[E](e, p, 4)))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e)
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, T <: Product](construct: (A, B, C, D, E, F) => T,
        a: String, b: String, c: String, d: String, e: String, f: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1,
      toField[C](c, p, 2,
      toField[D](d, p, 3,
      toField[E](e, p, 4,
      toField[F](f, p, 5))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f)
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, T <: Product](construct: (A, B, C, D, E, F, G) => T,
        a: String, b: String, c: String, d: String, e: String, f: String, g: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1,
      toField[C](c, p, 2,
      toField[D](d, p, 3,
      toField[E](e, p, 4,
      toField[F](f, p, 5,
      toField[G](g, p, 6)))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f),
      fromField[G](value, g)
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H) => T,
        a: String, b: String, c: String, d: String, e: String, f: String, g: String, h: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1,
      toField[C](c, p, 2,
      toField[D](d, p, 3,
      toField[E](e, p, 4,
      toField[F](f, p, 5,
      toField[G](g, p, 6,
      toField[H](h, p, 7))))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f),
      fromField[G](value, g),
      fromField[H](value, h)
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I) => T,
        a: String, b: String, c: String, d: String, e: String, f: String, g: String, h: String, i: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1,
      toField[C](c, p, 2,
      toField[D](d, p, 3,
      toField[E](e, p, 4,
      toField[F](f, p, 5,
      toField[G](g, p, 6,
      toField[H](h, p, 7,
      toField[I](i, p, 8)))))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f),
      fromField[G](value, g),
      fromField[H](value, h),
      fromField[I](value, i)
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1,
      toField[C](c, p, 2,
      toField[D](d, p, 3,
      toField[E](e, p, 4,
      toField[F](f, p, 5,
      toField[G](g, p, 6,
      toField[H](h, p, 7,
      toField[I](i, p, 8,
      toField[J](j, p, 9))))))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f),
      fromField[G](value, g),
      fromField[H](value, h),
      fromField[I](value, i),
      fromField[J](value, j)
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p, 0,
      toField[B](b, p, 1,
      toField[C](c, p, 2,
      toField[D](d, p, 3,
      toField[E](e, p, 4,
      toField[F](f, p, 5,
      toField[G](g, p, 6,
      toField[H](h, p, 7,
      toField[I](i, p, 8,
      toField[J](j, p, 9,
      toField[K](k, p, 10)))))))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f),
      fromField[G](value, g),
      fromField[H](value, h),
      fromField[I](value, i),
      fromField[J](value, j),
      fromField[K](value, k)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, L :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String, l: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p,  0,
      toField[B](b, p,  1,
      toField[C](c, p,  2,
      toField[D](d, p,  3,
      toField[E](e, p,  4,
      toField[F](f, p,  5,
      toField[G](g, p,  6,
      toField[H](h, p,  7,
      toField[I](i, p,  8,
      toField[J](j, p,  9,
      toField[K](k, p, 10,
      toField[L](l, p, 11))))))))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f),
      fromField[G](value, g),
      fromField[H](value, h),
      fromField[I](value, i),
      fromField[J](value, j),
      fromField[K](value, k),
      fromField[L](value, l)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, L :JF, M :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L, M) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String, l: String, m: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p,  0,
      toField[B](b, p,  1,
      toField[C](c, p,  2,
      toField[D](d, p,  3,
      toField[E](e, p,  4,
      toField[F](f, p,  5,
      toField[G](g, p,  6,
      toField[H](h, p,  7,
      toField[I](i, p,  8,
      toField[J](j, p,  9,
      toField[K](k, p, 10,
      toField[L](l, p, 11,
      toField[M](m, p, 12)))))))))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f),
      fromField[G](value, g),
      fromField[H](value, h),
      fromField[I](value, i),
      fromField[J](value, j),
      fromField[K](value, k),
      fromField[L](value, l),
      fromField[M](value, m)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, L :JF, M :JF, N :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String, l: String, m: String, n: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p,  0,
      toField[B](b, p,  1,
      toField[C](c, p,  2,
      toField[D](d, p,  3,
      toField[E](e, p,  4,
      toField[F](f, p,  5,
      toField[G](g, p,  6,
      toField[H](h, p,  7,
      toField[I](i, p,  8,
      toField[J](j, p,  9,
      toField[K](k, p, 10,
      toField[L](l, p, 11,
      toField[M](m, p, 12,
      toField[N](n, p, 13))))))))))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f),
      fromField[G](value, g),
      fromField[H](value, h),
      fromField[I](value, i),
      fromField[J](value, j),
      fromField[K](value, k),
      fromField[L](value, l),
      fromField[M](value, m),
      fromField[N](value, n)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, L :JF, M :JF, N :JF, O :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String, l: String, m: String, n: String, o: String) = new JF[T]{
    def write(p: T) = JsObject(
      toField[A](a, p,  0,
      toField[B](b, p,  1,
      toField[C](c, p,  2,
      toField[D](d, p,  3,
      toField[E](e, p,  4,
      toField[F](f, p,  5,
      toField[G](g, p,  6,
      toField[H](h, p,  7,
      toField[I](i, p,  8,
      toField[J](j, p,  9,
      toField[K](k, p, 10,
      toField[L](l, p, 11,
      toField[M](m, p, 12,
      toField[N](n, p, 13,
      toField[O](o, p, 14)))))))))))))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d),
      fromField[E](value, e),
      fromField[F](value, f),
      fromField[G](value, g),
      fromField[H](value, h),
      fromField[I](value, i),
      fromField[J](value, j),
      fromField[K](value, k),
      fromField[L](value, l),
      fromField[M](value, m),
      fromField[N](value, n),
      fromField[O](value, o)
    )
  }

  // helpers
  
  private def toField[T](fieldName: String, p: Product, ix: Int, rest: List[JsField] = Nil)
                        (implicit writer: JsonWriter[T]): List[JsField] = {
    val value = p.productElement(ix).asInstanceOf[T]
    writer match {
      case _: OptionFormat[_] if (value == None) => rest
      case _ => JsField(fieldName, writer.write(value)) :: rest
    }
  }
  
  private def fromField[T](value: JsValue, fieldName: String)(implicit reader: JsonReader[T]) = {
    @tailrec
    def getFrom(fields: List[JsField]): T = {
      if (fields.isEmpty) {
        if (reader.isInstanceOf[OptionFormat[_]]) None.asInstanceOf[T]
        else throw new DeserializationException("Object is missing required member '" + fieldName + "'")
      } else if (fields.head.name == fieldName) {
        reader.read(fields.head.value)
      } else {
        getFrom(fields.tail)
      }
    }
    value match {
      case x: JsObject => getFrom(x.fields)
      case _ => throw new DeserializationException("Object expected")
    }
  }
}
