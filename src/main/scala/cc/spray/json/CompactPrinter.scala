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

/**
  * A JsonPrinter that produces compact JSON source without any superfluous whitespace.
 */
trait CompactPrinter extends JsonPrinter {

  def print(x: JsValue, sb: StringBuilder) {
    x match {
      case JsObject(x) => printObject(x, sb)
      case JsArray(x)  => printArray(x, sb)
      case _ => printLeaf(x, sb)
    }
  }

  private def printObject(members: Map[String, JsValue], sb: StringBuilder) {
    sb.append('{')
    printSeq(members, sb.append(',')) { m =>
      printString(m._1, sb)
      sb.append(':')
      print(m._2, sb)
    }
    sb.append('}')
  }

  private def printArray(elements: List[JsValue], sb: StringBuilder) {
    sb.append('[')
    printSeq(elements, sb.append(','))(print(_, sb))
    sb.append(']')
  }
}

object CompactPrinter extends CompactPrinter