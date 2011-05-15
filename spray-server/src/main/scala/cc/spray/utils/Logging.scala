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

package cc.spray.utils

import akka.event.EventHandler._
import String.format

/**
  * Defines simple convenience logging methods that delegate to the Akka EventHandler.
  * They only model logging calls generating log entries of type String, formatted via String.format with zero to
  * three arguments. For most logging use cases this is enough.
  * You will probably want to extend this trait, use your own or call the EventHandler directly in any one of these cases
  * 
  *  - You'd like to log objects that are not Strings
  *  - You'd like to supply more than three arguments to your format string
  *  - You create high-frequency logging messages in performance-critical parts of your application
  *  
  *  In the last case it is better to not use String.format (due to its somewhat lower performance) but instead use
  *  some faster, more specialized String formatting, like the [[org.slf4j.helpers.MessageFormatter]].
  */
trait Logging {
  
  def logDebug(msg: => String) { debug(this, msg) }
  def logDebug(msgFmt: String, a: => Any) { debug(this, format(msgFmt, box(a))) }
  def logDebug(msgFmt: String, a: => Any, b: => Any) { debug(this, format(msgFmt, box(a), box(b))) }
  def logDebug(msgFmt: String, a: => Any, b: => Any, c: => Any) { debug(this, format(msgFmt, box(a), box(b), box(c))) }
  
  def logInfo(msg: => String) { info(this, msg) }
  def logInfo(msgFmt: String, a: => Any) { info(this, format(msgFmt, box(a))) }
  def logInfo(msgFmt: String, a: => Any, b: => Any) { info(this, format(msgFmt, box(a), box(b))) }
  def logInfo(msgFmt: String, a: => Any, b: => Any, c: => Any) { info(this, format(msgFmt, box(a), box(b), box(c))) }
  
  def logWarn(msg: => String) { warning(this, msg) }
  def logWarn(msgFmt: String, a: => Any) { warning(this, format(msgFmt, box(a))) }
  def logWarn(msgFmt: String, a: => Any, b: => Any) { warning(this, format(msgFmt, box(a), box(b))) }
  def logWarn(msgFmt: String, a: => Any, b: => Any, c: => Any) { warning(this, format(msgFmt, box(a), box(b), box(c))) }
  
  def logError(msg: => String) { error(this, msg) }
  def logError(msgFmt: String, a: => Any) { error(this, format(msgFmt, box(a))) }
  def logError(msgFmt: String, a: => Any, b: => Any) { error(this, format(msgFmt, box(a), box(b))) }
  def logError(msgFmt: String, a: => Any, b: => Any, c: => Any) { error(this, format(msgFmt, box(a), box(b), box(c))) }
  def logError(cause: Throwable, msg: => String) { error(cause, this, msg) }
  def logError(cause: Throwable, msgFmt: String, a: => Any) { error(cause, this, format(msgFmt, box(a))) }
  def logError(cause: Throwable, msgFmt: String, a: => Any, b: => Any) { error(cause, this, format(msgFmt, box(a), box(b))) }
  def logError(cause: Throwable, msgFmt: String, a: => Any, b: => Any, c: => Any) { error(cause, this, format(msgFmt, box(a), box(b), box(c))) }  

  @inline
  private def box(a: Any) = a.asInstanceOf[AnyRef]
}