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

package spray.json

import java.lang.StringBuilder
import annotation.tailrec

/**
  * A JsonPrinter that produces a nicely readable JSON source.
 */
trait PrettyPrinter extends JsonPrinter {
  val Indent = 2

  def print(x: JsValue, sb: StringBuilder) {
    print(x, sb, 0)
  }
  
  private def print(x: JsValue, sb: StringBuilder, indent: Int) {
    x match {
      case JsObject(x) => printObject(x, sb, indent)
      case JsArray(x)  => printArray(x, sb, indent)
      case _ => printLeaf(x, sb)
    }
  }

  private def printObject(members: Map[String, JsValue], sb: StringBuilder, indent: Int) {
    sb.append("{\n")    
    printSeq(members, sb.append(",\n")) { m =>
      printIndent(sb, indent + Indent)
      printString(m._1, sb)
      sb.append(": ")
      print(m._2, sb, indent + Indent)
    }
    sb.append('\n')
    printIndent(sb, indent)
    sb.append("}")
  }
  
  private def printArray(elements: List[JsValue], sb: StringBuilder, indent: Int) {
    sb.append('[')
    printSeq(elements, sb.append(", "))(print(_, sb, indent))
    sb.append(']')
  }
  
  @tailrec
  private def printIndent(sb: StringBuilder, indent: Int) {
    if (indent > 0) {
      sb.append(' ')
      printIndent(sb, indent - 1)
    }
  }
}

object PrettyPrinter extends PrettyPrinter