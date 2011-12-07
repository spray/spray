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

package cc.spray.json

import annotation.tailrec

/**
 * Provides the helpers for constructing custom JsonFormat implementations for types implementing the Product trait
 * (especially case classes)
 */
trait ProductFormats {
  this: StandardFormats =>

  def jsonFormat[A :JF, T <: Product](construct: A => T, a: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0)
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a)
    )
  }

  def jsonFormat[A :JF, B :JF, T <: Product](construct: (A, B) => T, a: String, b: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, T <: Product](construct: (A, B, C) => T,
        a: String, b: String, c: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1,
      productElement2Field[C](c, p, 2)))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, T <: Product](construct: (A, B, C, D) => T,
        a: String, b: String, c: String, d: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1,
      productElement2Field[C](c, p, 2,
      productElement2Field[D](d, p, 3))))
    )
    def read(value: JsValue) = construct(
      fromField[A](value, a),
      fromField[B](value, b),
      fromField[C](value, c),
      fromField[D](value, d)
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, T <: Product](construct: (A, B, C, D, E) => T,
        a: String, b: String, c: String, d: String, e: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1,
      productElement2Field[C](c, p, 2,
      productElement2Field[D](d, p, 3,
      productElement2Field[E](e, p, 4)))))
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
        a: String, b: String, c: String, d: String, e: String, f: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1,
      productElement2Field[C](c, p, 2,
      productElement2Field[D](d, p, 3,
      productElement2Field[E](e, p, 4,
      productElement2Field[F](f, p, 5))))))
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
        a: String, b: String, c: String, d: String, e: String, f: String, g: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1,
      productElement2Field[C](c, p, 2,
      productElement2Field[D](d, p, 3,
      productElement2Field[E](e, p, 4,
      productElement2Field[F](f, p, 5,
      productElement2Field[G](g, p, 6)))))))
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
         a: String, b: String, c: String, d: String, e: String, f: String, g: String, h: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1,
      productElement2Field[C](c, p, 2,
      productElement2Field[D](d, p, 3,
      productElement2Field[E](e, p, 4,
      productElement2Field[F](f, p, 5,
      productElement2Field[G](g, p, 6,
      productElement2Field[H](h, p, 7))))))))
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
        (construct: (A, B, C, D, E, F, G, H, I) => T, a: String, b: String, c: String, d: String, e: String, f: String,
         g: String, h: String, i: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1,
      productElement2Field[C](c, p, 2,
      productElement2Field[D](d, p, 3,
      productElement2Field[E](e, p, 4,
      productElement2Field[F](f, p, 5,
      productElement2Field[G](g, p, 6,
      productElement2Field[H](h, p, 7,
      productElement2Field[I](i, p, 8)))))))))
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
         f: String, g: String, h: String, i: String, j: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1,
      productElement2Field[C](c, p, 2,
      productElement2Field[D](d, p, 3,
      productElement2Field[E](e, p, 4,
      productElement2Field[F](f, p, 5,
      productElement2Field[G](g, p, 6,
      productElement2Field[H](h, p, 7,
      productElement2Field[I](i, p, 8,
      productElement2Field[J](j, p, 9))))))))))
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
         f: String, g: String, h: String, i: String, j: String, k: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p, 0,
      productElement2Field[B](b, p, 1,
      productElement2Field[C](c, p, 2,
      productElement2Field[D](d, p, 3,
      productElement2Field[E](e, p, 4,
      productElement2Field[F](f, p, 5,
      productElement2Field[G](g, p, 6,
      productElement2Field[H](h, p, 7,
      productElement2Field[I](i, p, 8,
      productElement2Field[J](j, p, 9,
      productElement2Field[K](k, p, 10)))))))))))
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
         f: String, g: String, h: String, i: String, j: String, k: String, l: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p,  0,
      productElement2Field[B](b, p,  1,
      productElement2Field[C](c, p,  2,
      productElement2Field[D](d, p,  3,
      productElement2Field[E](e, p,  4,
      productElement2Field[F](f, p,  5,
      productElement2Field[G](g, p,  6,
      productElement2Field[H](h, p,  7,
      productElement2Field[I](i, p,  8,
      productElement2Field[J](j, p,  9,
      productElement2Field[K](k, p, 10,
      productElement2Field[L](l, p, 11))))))))))))
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
         f: String, g: String, h: String, i: String, j: String, k: String, l: String, m: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p,  0,
      productElement2Field[B](b, p,  1,
      productElement2Field[C](c, p,  2,
      productElement2Field[D](d, p,  3,
      productElement2Field[E](e, p,  4,
      productElement2Field[F](f, p,  5,
      productElement2Field[G](g, p,  6,
      productElement2Field[H](h, p,  7,
      productElement2Field[I](i, p,  8,
      productElement2Field[J](j, p,  9,
      productElement2Field[K](k, p, 10,
      productElement2Field[L](l, p, 11,
      productElement2Field[M](m, p, 12)))))))))))))
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
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => T, a: String, b: String, c: String, d: String,
         e: String, f: String, g: String, h: String, i: String, j: String, k: String, l: String, m: String,
         n: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p,  0,
      productElement2Field[B](b, p,  1,
      productElement2Field[C](c, p,  2,
      productElement2Field[D](d, p,  3,
      productElement2Field[E](e, p,  4,
      productElement2Field[F](f, p,  5,
      productElement2Field[G](g, p,  6,
      productElement2Field[H](h, p,  7,
      productElement2Field[I](i, p,  8,
      productElement2Field[J](j, p,  9,
      productElement2Field[K](k, p, 10,
      productElement2Field[L](l, p, 11,
      productElement2Field[M](m, p, 12,
      productElement2Field[N](n, p, 13))))))))))))))
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
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => T, a: String, b: String, c: String, d: String,
         e: String, f: String, g: String, h: String, i: String, j: String, k: String, l: String, m: String, n: String,
         o: String) = new RootJsonFormat[T]{
    def write(p: T) = JsObject(
      productElement2Field[A](a, p,  0,
      productElement2Field[B](b, p,  1,
      productElement2Field[C](c, p,  2,
      productElement2Field[D](d, p,  3,
      productElement2Field[E](e, p,  4,
      productElement2Field[F](f, p,  5,
      productElement2Field[G](g, p,  6,
      productElement2Field[H](h, p,  7,
      productElement2Field[I](i, p,  8,
      productElement2Field[J](j, p,  9,
      productElement2Field[K](k, p, 10,
      productElement2Field[L](l, p, 11,
      productElement2Field[M](m, p, 12,
      productElement2Field[N](n, p, 13,
      productElement2Field[O](o, p, 14)))))))))))))))
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
  
  protected def productElement2Field[T](fieldName: String, p: Product, ix: Int, rest: List[JsField] = Nil)
                                       (implicit writer: JsonWriter[T]): List[JsField] = {
    val value = p.productElement(ix).asInstanceOf[T]
    writer match {
      case _: OptionFormat[_] if (value == None) => rest
      case _ => (fieldName, writer.write(value)) :: rest
    }
  }
  
  private def fromField[T](value: JsValue, fieldName: String)(implicit reader: JsonReader[T]) = {
    value match {
      case x: JsObject =>
        var fieldFound = false
        try {
          val fieldValue = x.fields(fieldName)
          fieldFound = true
          reader.read(fieldValue)
        }
        catch {
          case e: NoSuchElementException if !fieldFound =>
            if (reader.isInstanceOf[OptionFormat[_]]) None.asInstanceOf[T]
            else throw new DeserializationException("Object is missing required member '" + fieldName + "'", e)
        }
      case _ => throw new DeserializationException("Object expected")
    }
  }
}

/**
 * This trait supplies an alternative rendering mode for optional case class members.
 * Normally optional members that are undefined (`None`) are not rendered at all.
 * By mixing in this trait into your custom JsonProtocol you can enforce the rendering of undefined members as `null`.
 * (Note that this only affect JSON writing, spray-json will always read missing optional members as well as `null`
 * optional members as `None`.)
 */
trait NullOptions extends ProductFormats {
  this: StandardFormats =>

  override protected def productElement2Field[T](fieldName: String, p: Product, ix: Int, rest: List[JsField])
                                                (implicit writer: JsonWriter[T]) = {
    val value = p.productElement(ix).asInstanceOf[T]
    (fieldName, writer.write(value)) :: rest
  }
}