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

package cc.spray.util
package pimps


class PimpedProduct(product: Product) {

  def productJoin(that: Product): Product = product match {
    case Product0 => that
    case p: Product1[_] => that match {
      case Product0 => p
      case q: Product1[_] => (p._1, q._1)
      case q: Product2[_, _] => (p._1, q._1, q._2)
      case q: Product3[_, _, _] => (p._1, q._1, q._2, q._3)
      case q: Product4[_, _, _, _] => (p._1, q._1, q._2, q._3, q._4)
      case q: Product5[_, _, _, _, _] => (p._1, q._1, q._2, q._3, q._4, q._5)
      case q: Product6[_, _, _, _, _, _] => (p._1, q._1, q._2, q._3, q._4, q._5, q._6)
      case q: Product7[_, _, _, _, _, _, _] => (p._1, q._1, q._2, q._3, q._4, q._5, q._6, q._7)
      case q: Product8[_, _, _, _, _, _, _, _] => (p._1, q._1, q._2, q._3, q._4, q._5, q._6, q._7, q._8)
      case _ => throw new NotImplementedException("productJoin with " + that)
    }
    case p: Product2[_, _] => that match {
      case Product0 => p
      case q: Product1[_] => (p._1, p._2, q._1)
      case q: Product2[_, _] => (p._1, p._2, q._1, q._2)
      case q: Product3[_, _, _] => (p._1, p._2, q._1, q._2, q._3)
      case q: Product4[_, _, _, _] => (p._1, p._2, q._1, q._2, q._3, q._4)
      case q: Product5[_, _, _, _, _] => (p._1, p._2, q._1, q._2, q._3, q._4, q._5)
      case q: Product6[_, _, _, _, _, _] => (p._1, p._2, q._1, q._2, q._3, q._4, q._5, q._6)
      case q: Product7[_, _, _, _, _, _, _] => (p._1, p._2, q._1, q._2, q._3, q._4, q._5, q._6, q._7)
      case _ => throw new NotImplementedException("productJoin with " + that)
    }
    case p: Product3[_, _, _] => that match {
      case Product0 => p
      case q: Product1[_] => (p._1, p._2, p._3, q._1)
      case q: Product2[_, _] => (p._1, p._2, p._3, q._1, q._2)
      case q: Product3[_, _, _] => (p._1, p._2, p._3, q._1, q._2, q._3)
      case q: Product4[_, _, _, _] => (p._1, p._2, p._3, q._1, q._2, q._3, q._4)
      case q: Product5[_, _, _, _, _] => (p._1, p._2, p._3, q._1, q._2, q._3, q._4, q._5)
      case q: Product6[_, _, _, _, _, _] => (p._1, p._2, p._3, q._1, q._2, q._3, q._4, q._5, q._6)
      case _ => throw new NotImplementedException("productJoin with " + that)
    }
    case p: Product4[_, _, _, _] => that match {
      case Product0 => p
      case q: Product1[_] => (p._1, p._2, p._3, p._4, q._1)
      case q: Product2[_, _] => (p._1, p._2, p._3, p._4, q._1, q._2)
      case q: Product3[_, _, _] => (p._1, p._2, p._3, p._4, q._1, q._2, q._3)
      case q: Product4[_, _, _, _] => (p._1, p._2, p._3, p._4, q._1, q._2, q._3, q._4)
      case q: Product5[_, _, _, _, _] => (p._1, p._2, p._3, p._4, q._1, q._2, q._3, q._4, q._5)
      case _ => throw new NotImplementedException("productJoin with " + that)
    }
    case p: Product5[_, _, _, _, _] => that match {
      case Product0 => p
      case q: Product1[_] => (p._1, p._2, p._3, p._4, p._5, q._1)
      case q: Product2[_, _] => (p._1, p._2, p._3, p._4, p._5, q._1, q._2)
      case q: Product3[_, _, _] => (p._1, p._2, p._3, p._4, p._5, q._1, q._2, q._3)
      case q: Product4[_, _, _, _] => (p._1, p._2, p._3, p._4, p._5, q._1, q._2, q._3, q._4)
      case _ => throw new NotImplementedException("productJoin with " + that)
    }
    case p: Product6[_, _, _, _, _, _] => that match {
      case Product0 => p
      case q: Product1[_] => (p._1, p._2, p._3, p._4, p._5, p._6, q._1)
      case q: Product2[_, _] => (p._1, p._2, p._3, p._4, p._5, p._6, q._1, q._2)
      case q: Product3[_, _, _] => (p._1, p._2, p._3, p._4, p._5, p._6, q._1, q._2, q._3)
      case _ => throw new NotImplementedException("productJoin with " + that)
    }
    case p: Product7[_, _, _, _, _, _, _] => that match {
      case Product0 => p
      case q: Product1[_] => (p._1, p._2, p._3, p._4, p._5, p._6, p._7, q._1)
      case q: Product2[_, _] => (p._1, p._2, p._3, p._4, p._5, p._6, p._7, q._1, q._2)
      case _ => throw new NotImplementedException("productJoin with " + that)
    }
    case p: Product8[_, _, _, _, _, _, _, _] => that match {
      case Product0 => p
      case q: Product1[_] => (p._1, p._2, p._3, p._4, p._5, p._6, p._7, p._8, q._1)
      case _ => throw new NotImplementedException("productJoin with " + that)
    }
    case p: Product9[_, _, _, _, _, _, _, _, _] => that match {
      case Product0 => p
      case _ => throw new NotImplementedException("productJoin with " + that)
    }
    case _ => throw new NotImplementedException("productJoin on " + product)
  }

}

sealed class Product0 extends Product {
  def canEqual(that: Any) = that.isInstanceOf[Product0]

  def productArity = 0

  def productElement(n: Int) = throw new IllegalStateException
}

object Product0 extends Product0