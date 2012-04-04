/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.can
package parsing

class ErrorState(val message: String, val status: Int) extends ParsingState {
  override def hashCode = message.## * 31 + status
  override def equals(obj: Any) = obj match {
    case x: ErrorState => x.message == message && x.status == status
    case _ => false
  }
  override def toString = "ErrorState(" + message + ", " + status + ")"
}

object ErrorState {
  def apply(message: String, status: Int = 400) = new ErrorState(
    message.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n"),
    status
  )
}
