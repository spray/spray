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

/**
 * Provides the helpers for constructing custom JsonFormat implementations for types implementing the Product trait
 * (especially case classes)
 */
trait ProductFormats {

  private type JF[T] = JsonFormat[T] // simple alias for reduced verbosity

  def jsonFormat[A :JF, T <: Product](construct: A => T, a: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A]
    )
  }

  def jsonFormat[A :JF, B :JF, T <: Product](construct: (A, B) => T, a: String, b: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B]
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, T <: Product](construct: (A, B, C) => T,
                                                a: String, b: String, c: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C]
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, T <: Product](construct: (A, B, C, D) => T,
                                                       a: String, b: String, c: String, d: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D]
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, T <: Product](construct: (A, B, C, D, E) => T,
        a: String, b: String, c: String, d: String, e: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E]
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, T <: Product](construct: (A, B, C, D, E, F) => T,
        a: String, b: String, c: String, d: String, e: String, f: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F]
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, T <: Product](construct: (A, B, C, D, E, F, G) => T,
        a: String, b: String, c: String, d: String, e: String, f: String, g: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson),
      JsField(g, element[G](p, 6).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F],
      field(value, g).fromJson[G]
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H) => T,
        a: String, b: String, c: String, d: String, e: String, f: String, g: String, h: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson),
      JsField(g, element[G](p, 6).toJson),
      JsField(h, element[H](p, 7).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F],
      field(value, g).fromJson[G],
      field(value, h).fromJson[H]
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I) => T,
        a: String, b: String, c: String, d: String, e: String, f: String, g: String, h: String, i: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson),
      JsField(g, element[G](p, 6).toJson),
      JsField(h, element[H](p, 7).toJson),
      JsField(i, element[I](p, 8).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F],
      field(value, g).fromJson[G],
      field(value, h).fromJson[H],
      field(value, i).fromJson[I]
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson),
      JsField(g, element[G](p, 6).toJson),
      JsField(h, element[H](p, 7).toJson),
      JsField(i, element[I](p, 8).toJson),
      JsField(j, element[J](p, 9).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F],
      field(value, g).fromJson[G],
      field(value, h).fromJson[H],
      field(value, i).fromJson[I],
      field(value, j).fromJson[J]
    )
  }
  
  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson),
      JsField(g, element[G](p, 6).toJson),
      JsField(h, element[H](p, 7).toJson),
      JsField(i, element[I](p, 8).toJson),
      JsField(j, element[J](p, 9).toJson),
      JsField(k, element[K](p, 10).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F],
      field(value, g).fromJson[G],
      field(value, h).fromJson[H],
      field(value, i).fromJson[I],
      field(value, j).fromJson[J],
      field(value, k).fromJson[K]
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, L :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String, l: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson),
      JsField(g, element[G](p, 6).toJson),
      JsField(h, element[H](p, 7).toJson),
      JsField(i, element[I](p, 8).toJson),
      JsField(j, element[J](p, 9).toJson),
      JsField(k, element[K](p, 10).toJson),
      JsField(l, element[L](p, 11).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F],
      field(value, g).fromJson[G],
      field(value, h).fromJson[H],
      field(value, i).fromJson[I],
      field(value, j).fromJson[J],
      field(value, k).fromJson[K],
      field(value, l).fromJson[L]
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, L :JF, M :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L, M) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String, l: String, m: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson),
      JsField(g, element[G](p, 6).toJson),
      JsField(h, element[H](p, 7).toJson),
      JsField(i, element[I](p, 8).toJson),
      JsField(j, element[J](p, 9).toJson),
      JsField(k, element[K](p, 10).toJson),
      JsField(l, element[L](p, 11).toJson),
      JsField(m, element[M](p, 12).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F],
      field(value, g).fromJson[G],
      field(value, h).fromJson[H],
      field(value, i).fromJson[I],
      field(value, j).fromJson[J],
      field(value, k).fromJson[K],
      field(value, l).fromJson[L],
      field(value, m).fromJson[M]
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, L :JF, M :JF, N :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String, l: String, m: String, n: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson),
      JsField(g, element[G](p, 6).toJson),
      JsField(h, element[H](p, 7).toJson),
      JsField(i, element[I](p, 8).toJson),
      JsField(j, element[J](p, 9).toJson),
      JsField(k, element[K](p, 10).toJson),
      JsField(l, element[L](p, 11).toJson),
      JsField(m, element[M](p, 12).toJson),
      JsField(n, element[N](p, 13).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F],
      field(value, g).fromJson[G],
      field(value, h).fromJson[H],
      field(value, i).fromJson[I],
      field(value, j).fromJson[J],
      field(value, k).fromJson[K],
      field(value, l).fromJson[L],
      field(value, m).fromJson[M],
      field(value, n).fromJson[N]
    )
  }

  def jsonFormat[A :JF, B :JF, C :JF, D :JF, E :JF, F :JF, G :JF, H :JF, I :JF, J :JF, K :JF, L :JF, M :JF, N :JF, O :JF, T <: Product]
        (construct: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => T, a: String, b: String, c: String, d: String, e: String,
         f: String, g: String, h: String, i: String, j: String, k: String, l: String, m: String, n: String, o: String) = new JF[T]{
    def write(p: T) = JsObject(
      JsField(a, element[A](p, 0).toJson),
      JsField(b, element[B](p, 1).toJson),
      JsField(c, element[C](p, 2).toJson),
      JsField(d, element[D](p, 3).toJson),
      JsField(e, element[E](p, 4).toJson),
      JsField(f, element[F](p, 5).toJson),
      JsField(g, element[G](p, 6).toJson),
      JsField(h, element[H](p, 7).toJson),
      JsField(i, element[I](p, 8).toJson),
      JsField(j, element[J](p, 9).toJson),
      JsField(k, element[K](p, 10).toJson),
      JsField(l, element[L](p, 11).toJson),
      JsField(m, element[M](p, 12).toJson),
      JsField(n, element[N](p, 13).toJson),
      JsField(o, element[O](p, 14).toJson)
    )
    def read(value: JsValue) = construct(
      field(value, a).fromJson[A],
      field(value, b).fromJson[B],
      field(value, c).fromJson[C],
      field(value, d).fromJson[D],
      field(value, e).fromJson[E],
      field(value, f).fromJson[F],
      field(value, g).fromJson[G],
      field(value, h).fromJson[H],
      field(value, i).fromJson[I],
      field(value, j).fromJson[J],
      field(value, k).fromJson[K],
      field(value, l).fromJson[L],
      field(value, m).fromJson[M],
      field(value, n).fromJson[N],
      field(value, o).fromJson[O]
    )
  }

  // helpers
  
  private def element[T](p: Product, ix: Int) = p.productElement(ix).asInstanceOf[T]
  
  private def field(value: JsValue, fieldName: String) = value match {
    case jso: JsObject => {
      jso.fields
              .find(_.name == fieldName)
              .getOrElse(throw new DeserializationException("Object is missing required member '" + fieldName + "'"))
              .value
    }
    case _ => throw new DeserializationException("Object expected")
  }
}
