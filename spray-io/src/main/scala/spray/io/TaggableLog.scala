/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.io

import akka.event.{Logging, LoggingAdapter}


/**
 * Interface that can be implemented by `tag` objects on a connection in order to
 * prefix all log messages created by spray-io for this connection with a custom marker.
 */
trait LogMarking {
  def logMarker: String
}

case class LogMark(logMarker: String) extends LogMarking

/**
 * Log allowing for message prefixing by tags implementing the LogMarking trait.
 * Needs to be created for a specific underlying LoggingAdapter and LogLevel by the companion.
 */
trait TaggableLog {
  def enabled: Boolean
  def log(tag: Any, template: String)
  def log(tag: Any, template: String, arg: Any)
  def log(tag: Any, template: String, arg1: Any, arg2: Any)
  def log(tag: Any, template: String, arg1: Any, arg2: Any, arg3: Any)
}

object TaggableLog {
  val NopLog = new TaggableLog {
    def enabled: Boolean = false
    def log(tag: Any, template: String) {}
    def log(tag: Any, template: String, arg: Any) {}
    def log(tag: Any, template: String, arg1: Any, arg2: Any) {}
    def log(tag: Any, template: String, arg1: Any, arg2: Any, arg3: Any) {}
  }

  def apply(la: LoggingAdapter, level: Logging.LogLevel): TaggableLog =
    if (la.isEnabled(level))
      new TaggableLog {
        def enabled: Boolean = true
        def log(tag: Any, template: String) { la.log(level, msg(tag, template)) }
        def log(tag: Any, template: String, arg: Any) { la.log(level, msg(tag, template), arg) }
        def log(tag: Any, template: String, a1: Any, a2: Any) { la.log(level, msg(tag, template), a1, a2) }
        def log(tag: Any, template: String, a1: Any, a2: Any, a3: Any) { la.log(level, msg(tag, template), a1, a2, a3) }
        private def msg(tag: Any, template: String) =
          tag match {
            case x: LogMarking =>
              val marker = x.logMarker
              if (marker.isEmpty) template else marker + ": " + template
            case _ => template
          }
      }
    else NopLog
}