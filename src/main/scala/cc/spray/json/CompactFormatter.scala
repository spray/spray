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

import java.lang.StringBuilder

/**
  * A JsonFormatter that produces compact JSON source without any superfluous whitespace.
 */
trait CompactFormatter extends JsonFormatter {

  def format(x: JsValue, sb: StringBuilder) {
    x match {
      case x: JsObject => formatObject(x.fields, sb)
      case x: JsArray => formatArray(x.elements, sb)
      case _ => printLeaf(x, sb)
    }
  }

  private def formatObject(members: Map[String, JsValue], sb: StringBuilder) {
    sb.append('{')
    printSeq(members, sb.append(',')) { m =>
      printString(m._1, sb)
      sb.append(':')
      format(m._2, sb)
    }
    sb.append('}')
  }

  private def formatArray(elements: Seq[JsValue], sb: StringBuilder) {
    sb.append('[')
    printSeq(elements, sb.append(','))(format(_, sb))
    sb.append(']')
  }
}

object CompactFormatter extends CompactFormatter