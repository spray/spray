/*
 * Copyright (C) 2009-2011 Mathias Doenitz
 * Inspired by a similar implementation by Nathan Hamblen
 * (https://github.com/n8han/Databinder-Dispatch)
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

import collection.mutable.ListBuffer

/**
  * The general type of a JSON AST node.
 */
sealed trait JsValue {
  override def toString = CompactPrinter(this)
  def toString(printer: (JsValue => String)) = printer(this)
  def fromJson[T :JsonReader]: T = jsonReader[T].read(this)
}
object JsValue {

  /**
    * General converter to a JsValue.
    * Throws an IllegalArgumentException if the given value cannot be converted.
   */
  def apply(value: Any): JsValue = value match {
    case null => JsNull
    case true => JsTrue
    case false => JsFalse
    case x: JsValue => x
    case x: String => JsString(x)
    case x: Int => JsNumber(x)
    case x: Long => JsNumber(x)
    case x: Double => JsNumber(x)
    case x: Char => JsString(String.valueOf(x))
    case x: Float => JsNumber(x)
    case x: Byte => JsNumber(x)
    case x: Short => JsNumber(x)
    case x: BigInt => JsNumber(x)
    case x: BigDecimal => JsNumber(x)
    case x: Symbol => JsString(x.name)
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
      case (key: JsString, value) => list += JsField(key.value, JsValue(value))
      case (x, _) => throw new IllegalArgumentException(x.toString + " cannot be converted to a JsString")
    }
    list.toList
  }
}

/**
  * A JSON object.
 */
case class JsObject(fields: List[JsField]) extends JsValue {
  lazy val asMap: Map[String, JsValue] = {
    val b = Map.newBuilder[String, JsValue]
    for (JsField(name, value) <- fields) b += ((name, value))
    b.result()
  }
}
object JsObject {
  def apply(members: JsField*) = new JsObject(members.toList)
}

/**
  * The members/fields of a JSON object.
 */
case class JsField(name: String, value: JsValue) extends JsValue
object JsField {
  def apply(name: String, value: Any) = new JsField(name, JsValue(value))
}

/**
  * A JSON array.
 */
case class JsArray(elements: List[JsValue]) extends JsValue
object JsArray {
  def apply(elements: JsValue*) = new JsArray(elements.toList)
}

/**
  * A JSON string.
 */
case class JsString(value: String) extends JsValue

/**
  * A JSON number.
 */
case class JsNumber(value: BigDecimal) extends JsValue
object JsNumber {
  def apply(n: Int) = new JsNumber(BigDecimal(n))
  def apply(n: Long) = new JsNumber(BigDecimal(n))
  def apply(n: Double) = n match {
    case n if n.isNaN      => JsNull
    case n if n.isInfinity => JsNull
    case _                 => new JsNumber(BigDecimal(n))
  }
  def apply(n: BigInt) = new JsNumber(BigDecimal(n))
  def apply(n: String) = new JsNumber(BigDecimal(n))
}

/**
  * JSON Booleans.
 */
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

/**
  * The representation for JSON null.
 */
case object JsNull extends JsValue
