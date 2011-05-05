package cc.spray.json
package formats

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

trait GenericFormats {

  private type JF[T] = JsonFormat[T] // simple alias for reduced verbosity
  
  /**
   * Lazy wrapper around serialization. Useful when you want to serialize mutually recursive structures.
   */
  def lazyFormat[T](format: => JF[T]) = new JF[T]{
    lazy val delegate = format;
    def write(x: T) = delegate.write(x);
    def read(value: JsValue) = delegate.read(value);
  }
  
  def format[A :JF, B :JF, T <: Product2[A, B]](an: String, bn: String)
                                               (construct: (A, B) => T) = new JF[T]{
    def write(p: T) = JsObject(JsField(an, p._1.toJson), JsField(bn, p._2.toJson))
    def read(value: JsValue) = value match {
      case JsObject(JsField(na, a) :: JsField(nb, b) :: Nil) if (na == an && nb == bn) => {
        construct(a.fromJson[A], b.fromJson[B])
      }
      case _ => throw new RuntimeException("Object with members '" + an + "' and '" + bn + "' expected")
    }
  }
  
  def format[A :JF, B :JF, C :JF, T <: Product3[A, B, C]](an: String, bn: String, cn: String)
                                                         (construct: (A, B, C) => T) = new JF[T]{
    def write(p: T) = JsObject(JsField(an, p._1.toJson), JsField(bn, p._2.toJson), JsField(cn, p._3.toJson))
    def read(value: JsValue) = value match {
      case JsObject(JsField(na, a) :: JsField(nb, b):: JsField(nc, c) :: Nil) if (na == an && nb == bn && nc == cn) => {
        construct(a.fromJson[A], b.fromJson[B], c.fromJson[C])
      }
      case _ => throw new RuntimeException("Object with members '" + an + "', '" + bn + "' and '" + cn + "' expected")
    }
  }  

}
