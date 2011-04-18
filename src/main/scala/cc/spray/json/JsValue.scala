/*
 * Copyright (C) by the databinder-dispatch team
 * https://github.com/n8han/Databinder-Dispatch
 * 
 * Adapted in 2011 by Mathias Doenitz
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package cc.spray.json

import collection.mutable.ListBuffer

sealed trait JsValue {
  type T
  def self: T
  override def toString = CompactPrinter(this)
  def toString(printer: (JsValue => String)) = printer(this)
}

object JsValue {
  def apply(x: Any): JsValue = x match {
    case null => JsNull
    case true => JsTrue
    case false => JsFalse
    case x: JsValue => x
    case x: String => JsString(x)
    case x: Symbol => JsString(x.name)
    case x: Int => JsNumber(x)
    case x: Long => JsNumber(x)
    case x: Float => JsNumber(x)
    case x: Double => JsNumber(x)
    case x: BigInt => JsNumber(x)
    case x: BigDecimal => JsNumber(x)
    case x: collection.Map[_, _] => JsObject(fromSeq(x))
    case x@ collection.Seq((_, _), _*) => JsObject(fromSeq(x.asInstanceOf[Seq[(_, _)]]))
    case x: collection.Seq[_] => JsArray(x.toList.map(JsValue.apply))
    case x => throw new IllegalArgumentException(x.toString + " cannot be converted to a JsValue")
  }
  
  private def fromSeq(seq: Iterable[(_, _)]) = {
    val list = ListBuffer.empty[(JsString, JsValue)]
    seq.foreach {
      case (key: String, value) => list += ((JsString(key), JsValue(value)))
      case (key: Symbol, value) => list += ((JsString(key.name), JsValue(value)))
      case (key: JsString, value) => list += ((key, JsValue(value)))
      case (x, _) => throw new IllegalArgumentException(x.toString + " cannot be converted to a JsString")
    }
    list.toList
  }

  def fromString(json: String) = JsonParser(json)
  def toString(value: JsValue, printer: (JsValue => String) = CompactPrinter) = printer(value)
}

case class JsString(override val self: String) extends JsValue {
  type T = String
}

/**
 * This can also be implemented with as a Double, even though BigDecimal is
 * more loyal to the json spec.
 *  NOTE: Subtle bugs can arise, i.e.
 *    BigDecimal(3.14) != BigDecimal("3.14")
 * such are the perils of floating point arithmetic.
 */
case class JsNumber(override val self: BigDecimal) extends JsValue {
  type T = BigDecimal
}

object JsNumber {
  def apply(n: Int) = new JsNumber(BigDecimal(n))
  def apply(n: Long) = new JsNumber(BigDecimal(n))
  def apply(n: Float) = new JsNumber(BigDecimal(n))
  def apply(n: Double) = new JsNumber(BigDecimal(n))
  def apply(n: BigInt) = new JsNumber(BigDecimal(n))
  def apply(n: String) = new JsNumber(BigDecimal(n))
}

// This can extend scala.collection.MapProxy to implement Map interface
case class JsObject(override val self: List[(JsString, JsValue)]) extends JsValue {
  type T = List[(JsString, JsValue)]
  lazy val asMap = self.toMap
}
object JsObject {
  def apply(members: (JsString, JsValue)*) = new JsObject(members.toList)
}

// This can extend scala.SeqProxy to implement Seq interface
case class JsArray(override val self: List[JsValue]) extends JsValue {
  type T = List[JsValue]
}
object JsArray {
  def apply(elements: JsValue*) = new JsArray(elements.toList)
}

sealed abstract class JsBoolean(val b: Boolean) extends JsValue {
  type T = Boolean
  val self = b
}

object JsBoolean {
  def unapply(x: JsBoolean): Option[Boolean] = Some(x.b)
}

case object JsTrue extends JsBoolean(true)

case object JsFalse extends JsBoolean(false)

case object JsNull extends JsValue {
  type T = Null
  val self = null
}
