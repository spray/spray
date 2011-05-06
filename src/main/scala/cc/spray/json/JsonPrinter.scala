/*
 * Copyright (C) 2009-2011 Mathias Doenitz
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
import java.lang.StringBuilder

/**
  * A JsonPrinter serializes a JSON AST to a String.
 */
trait JsonPrinter extends (JsValue => String) {

  def apply(x: JsValue) = {
    val sb = new StringBuilder
    print(x, sb)
    sb.toString
  }
  
  def print(x: JsValue, sb: StringBuilder)
  
  protected def printLeaf(x: JsValue, sb: StringBuilder) {
    x match {
      case JsNull      => sb.append("null")
      case JsTrue      => sb.append("true")
      case JsFalse     => sb.append("false")
      case JsNumber(x) => sb.append(x)
      case JsString(x) => printString(x, sb)
      case _           => throw new IllegalStateException
    }
  }

  protected def printString(s: String, sb: StringBuilder) {
    @tailrec
    def printEscaped(s: String, ix: Int) {
      if (ix < s.length) {
        s.charAt(ix) match {
          case '"' => sb.append("\\\"")
          case '\\' => sb.append("\\\\")
          case x if 0x20 <= x && x < 0x7F => sb.append(x)
          case '\b' => sb.append("\\b")
          case '\f' => sb.append("\\f")
          case '\n' => sb.append("\\n")
          case '\r' => sb.append("\\r")
          case '\t' => sb.append("\\t")
          case x if x <= 0xFF => sb.append("\\u00").append(Integer.toHexString(x))
          case x if x <= 0xFFF => sb.append("\\u0").append(Integer.toHexString(x))
          case x => sb.append("\\u").append(Integer.toHexString(x))
        }
        printEscaped(s, ix + 1)
      }
    }
    sb.append('"')
    printEscaped(s, 0)
    sb.append('"')
  }
  
  protected def printSeq[A](iterable: Iterable[A], printSeparator: => Unit)(f: A => Unit) {
    var first = true
    iterable.foreach { a =>
      if (first) first = false else printSeparator
      f(a)
    }
  }
} 