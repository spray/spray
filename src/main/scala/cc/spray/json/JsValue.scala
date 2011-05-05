/*
 * Original implementation (C) by the databinder-dispatch team
 * https://github.com/n8han/Databinder-Dispatch
 * Adapted and extended in 2011 by Mathias Doenitz
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

import formats._
import collection.mutable.ListBuffer

sealed trait JsValue {
  override def toString = CompactPrinter(this)
  def toString(printer: (JsValue => String)) = printer(this)
  def fromJson[T :JsonReader]: T = jsonReader.read(this)
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
    case x: Short => JsNumber(x)
    case x: Byte => JsNumber(x)
    case x: Float => JsNumber(x)
    case x: Double => JsNumber(x)
    case x: BigInt => JsNumber(x)
    case x: BigDecimal => JsNumber(x)
    case x: Char => JsString(String.valueOf(x))
    case x: collection.Map[_, _] => JsObject(fromSeq(x))
    case x@ collection.Seq((_, _), _*) => JsObject(fromSeq(x.asInstanceOf[Seq[(_, _)]]))
    case x: collection.Seq[_] => JsArray(x.toList.map(JsValue.apply))
    case x => throw new IllegalArgumentException(x.toString + " cannot be converted to a JsValue")
  }
  
  private def fromSeq(seq: Iterable[(_, _)]) = {
    val list = ListBuffer.empty[JsField]
    seq.foreach {
      case (key: String, value) => list += JsField(key, JsValue(value))
      case (key: Symbol, value) => list += JsField(key.name, JsValue(value))
      case (key: JsString, value) => list += JsField(key, JsValue(value))
      case (x, _) => throw new IllegalArgumentException(x.toString + " cannot be converted to a JsString")
    }
    list.toList
  }

  def fromString(json: String) = JsonParser(json)
  def toString(value: JsValue, printer: (JsValue => String) = CompactPrinter) = printer(value)
}

case class JsString(value: String) extends JsValue


case class JsNumber(value: BigDecimal) extends JsValue

object JsNumber {
  def apply(n: Int) = new JsNumber(BigDecimal(n))
  def apply(n: Long) = new JsNumber(BigDecimal(n))
  def apply(n: Double) = new JsNumber(BigDecimal(n))
  def apply(n: BigInt) = new JsNumber(BigDecimal(n))
  def apply(n: String) = new JsNumber(BigDecimal(n))
}

case class JsObject(fields: List[JsField]) extends JsValue {
  lazy val asMap: Map[String, JsValue] = {
    val b = Map.newBuilder[String, JsValue]
    for (JsField(name, value) <- fields) b += ((name.value, value))
    b.result()
  }
}

object JsObject {
  def apply(members: JsField*) = new JsObject(members.toList)
}


case class JsField(name: JsString, value: JsValue) extends JsValue

object JsField {
  def apply(name: String, value: Any) = new JsField(JsString(name), JsValue(value))
}


case class JsArray(elements: List[JsValue]) extends JsValue

object JsArray {
  def apply(elements: JsValue*) = new JsArray(elements.toList)
}


sealed trait JsBoolean extends JsValue {
  def value: Boolean
}

object JsBoolean {
  def apply(x: Boolean): JsBoolean = if (x) JsTrue else JsFalse
  def unapply(x: JsBoolean): Option[Boolean] = Some(x.value)
}

case object JsTrue extends JsBoolean {
  def value = true
}

case object JsFalse extends JsBoolean {
  def value = false
}


case object JsNull extends JsValue
