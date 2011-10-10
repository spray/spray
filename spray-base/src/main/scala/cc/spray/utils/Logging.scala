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

package cc.spray
package utils

import String.format
import java.io.{PrintWriter, StringWriter}
import org.slf4j.LoggerFactory

trait Logging {
  @transient
  lazy val log: Log = Logging.logCreator(this)
}
object Logging {
  lazy val logCreator: AnyRef => Log = SprayBaseSettings.LoggingTarget match {
    case "AkkaEventHandler" => new EventHandlerLog(_)
    case "SLF4J" => new Slf4jLog(_)
    case x => throw new RuntimeException("Unknown logging-target '" + x + "'configured in settings, expecting either " +
            "'AkkaEventHandler' or 'SLF4J'")
  }
}

/**
 * Defines a simple logging interface. It only models logging calls generating log entries of type String,
 * formatted via `String.format` with zero to three arguments. For most logging use cases this is enough.
 * You will probably want to extend this trait, use your own or call the underlying logging framework directly in any
 * one of these cases:
 *
 *  - You'd like to log objects that are not Strings
 *  - You'd like to supply more than three arguments to your format string
 *  - You create high-frequency logging messages in performance-critical parts of your application
 *
 *  In the last case it is better to not use `String.format` (due to its somewhat lower performance) but instead use
 *  some faster, more specialized String formatting, like the [[org.slf4j.helpers.MessageFormatter]].
 */
trait Log {
  def debug(msg: => String)
  def debug(msgFmt: String, a: => Any)
  def debug(msgFmt: String, a: => Any, b: => Any)
  def debug(msgFmt: String, a: => Any, b: => Any, c: => Any)
  
  def info(msg: => String)
  def info(msgFmt: String, a: => Any)
  def info(msgFmt: String, a: => Any, b: => Any)
  def info(msgFmt: String, a: => Any, b: => Any, c: => Any)
  
  def warn(msg: => String)
  def warn(msgFmt: String, a: => Any)
  def warn(msgFmt: String, a: => Any, b: => Any)
  def warn(msgFmt: String, a: => Any, b: => Any, c: => Any)
  
  def error(msg: => String)
  def error(msgFmt: String, a: => Any)
  def error(msgFmt: String, a: => Any, b: => Any)
  def error(msgFmt: String, a: => Any, b: => Any, c: => Any)
  def error(cause: Throwable, msg: => String)
  def error(cause: Throwable, msgFmt: String, a: => Any)
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any)
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any, c: => Any)  
}

/**
  * A Log that delegates to the Akka EventHandler.
  */
class EventHandlerLog(source: AnyRef) extends Log {
  import akka.event.{EventHandler => EH}
  
  def debug(msg: => String) { EH.debug(source, msg) }
  def debug(msgFmt: String, a: => Any) { EH.debug(source, format(msgFmt, box(a))) }
  def debug(msgFmt: String, a: => Any, b: => Any) { EH.debug(source, format(msgFmt, box(a), box(b))) }
  def debug(msgFmt: String, a: => Any, b: => Any, c: => Any) { EH.debug(source, format(msgFmt, box(a), box(b), box(c))) }
  
  def info(msg: => String) { EH.info(source, msg) }
  def info(msgFmt: String, a: => Any) { EH.info(source, format(msgFmt, box(a))) }
  def info(msgFmt: String, a: => Any, b: => Any) { EH.info(source, format(msgFmt, box(a), box(b))) }
  def info(msgFmt: String, a: => Any, b: => Any, c: => Any) { EH.info(source, format(msgFmt, box(a), box(b), box(c))) }
  
  def warn(msg: => String) { EH.warning(source, msg) }
  def warn(msgFmt: String, a: => Any) { EH.warning(source, format(msgFmt, box(a))) }
  def warn(msgFmt: String, a: => Any, b: => Any) { EH.warning(source, format(msgFmt, box(a), box(b))) }
  def warn(msgFmt: String, a: => Any, b: => Any, c: => Any) { EH.warning(source, format(msgFmt, box(a), box(b), box(c))) }
  
  def error(msg: => String) { EH.error(source, msg) }
  def error(msgFmt: String, a: => Any) { EH.error(source, format(msgFmt, box(a))) }
  def error(msgFmt: String, a: => Any, b: => Any) { EH.error(source, format(msgFmt, box(a), box(b))) }
  def error(msgFmt: String, a: => Any, b: => Any, c: => Any) { EH.error(source, format(msgFmt, box(a), box(b), box(c))) }
  def error(cause: Throwable, msg: => String) { EH.error(cause, source, msg) }
  def error(cause: Throwable, msgFmt: String, a: => Any) { EH.error(cause, source, format(msgFmt, box(a))) }
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any) { EH.error(cause, source, format(msgFmt, box(a), box(b))) }
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any, c: => Any) { EH.error(cause, source, format(msgFmt, box(a), box(b), box(c))) }  

  @inline
  private def box(a: Any) = a.asInstanceOf[AnyRef]
}

/**
 * A Log that simply prints all log messages directly to stdout.
 */
class ConsoleLog(source: AnyRef) extends Log {
  private val name = source.getClass.getName
  
  def debug(msg: => String) { println("[DEBUG] " + name + "\n\t" + msg) }
  def debug(msgFmt: String, a: => Any) { debug(format(msgFmt, &(a))) }
  def debug(msgFmt: String, a: => Any, b: => Any) { debug(format(msgFmt, &(a), &(b))) }
  def debug(msgFmt: String, a: => Any, b: => Any, c: => Any) { debug(format(msgFmt, &(a), &(b), &(c))) }

  def info(msg: => String) { println("[INFO ] " + name + "\n\t" + msg) }
  def info(msgFmt: String, a: => Any) { info(format(msgFmt, &(a))) }
  def info(msgFmt: String, a: => Any, b: => Any) { info(format(msgFmt, &(a), &(b))) }
  def info(msgFmt: String, a: => Any, b: => Any, c: => Any) { info(format(msgFmt, &(a), &(b), &(c))) }

  def warn(msg: => String) { println("[WARN ] " + name + "\n\t" + msg) }
  def warn(msgFmt: String, a: => Any) { warn(format(msgFmt, &(a))) }
  def warn(msgFmt: String, a: => Any, b: => Any) { warn(format(msgFmt, &(a), &(b))) }
  def warn(msgFmt: String, a: => Any, b: => Any, c: => Any) { warn(format(msgFmt, &(a), &(b), &(c))) }

  def error(msg: => String) { println("[ERROR] " + name + "\n\t" + msg) }
  def error(msgFmt: String, a: => Any) { error(format(msgFmt, &(a))) }
  def error(msgFmt: String, a: => Any, b: => Any) { error(format(msgFmt, &(a), &(b))) }
  def error(msgFmt: String, a: => Any, b: => Any, c: => Any) { error(format(msgFmt, &(a), &(b), &(c))) }
  def error(cause: Throwable, msg: => String) { println("[ERROR] " + name + "\n\t" + msg + "\n\t" + stackTrace(cause)) }
  def error(cause: Throwable, msgFmt: String, a: => Any) { error(cause, format(msgFmt, &(a))) }
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any) { error(cause, format(msgFmt, &(a), &(b))) }
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any, c: => Any) { error(cause, format(msgFmt, &(a), &(b), &(c))) }

  @inline
  private def &(a: Any) = a.asInstanceOf[AnyRef]

  private def stackTrace(e: Throwable) = {
    val sw = new StringWriter
    e.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}

/**
 * A Log that simply discards all log messages. Useful for example for testing.
 */
object NoLog extends Log {
  def debug(msg: => String) {}
  def debug(msgFmt: String, a: => Any) {}
  def debug(msgFmt: String, a: => Any, b: => Any) {}
  def debug(msgFmt: String, a: => Any, b: => Any, c: => Any) {}

  def info(msg: => String) {}
  def info(msgFmt: String, a: => Any) {}
  def info(msgFmt: String, a: => Any, b: => Any) {}
  def info(msgFmt: String, a: => Any, b: => Any, c: => Any) {}

  def warn(msg: => String) {}
  def warn(msgFmt: String, a: => Any) {}
  def warn(msgFmt: String, a: => Any, b: => Any) {}
  def warn(msgFmt: String, a: => Any, b: => Any, c: => Any) {}

  def error(msg: => String) {}
  def error(msgFmt: String, a: => Any) {}
  def error(msgFmt: String, a: => Any, b: => Any) {}
  def error(msgFmt: String, a: => Any, b: => Any, c: => Any) {}
  def error(cause: Throwable, msg: => String) {}
  def error(cause: Throwable, msgFmt: String, a: => Any) {}
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any) {}
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any, c: => Any) {}
}

/**
 * A Log that directly logs to SLF4J. If you want to use this you will have to pull in the SLF4J dependency plus the
 * underlying implementation (like LogBack) yourself since ''spray'' only depends on SLF4J with ''provided'' scope.
 */
class Slf4jLog(instance: AnyRef) extends Log {
  val l = LoggerFactory.getLogger(instance.getClass)

  def debug(msg: => String) { if (l.isDebugEnabled) l.debug(msg) }
  def debug(msgFmt: String, a: => Any) { if (l.isDebugEnabled) l.debug(format(msgFmt, &(a))) }
  def debug(msgFmt: String, a: => Any, b: => Any) { if (l.isDebugEnabled) l.debug(format(msgFmt, &(a), &(b))) }
  def debug(msgFmt: String, a: => Any, b: => Any, c: => Any) { if (l.isDebugEnabled) l.debug(format(msgFmt, &(a), &(b), &(c))) }

  def info(msg: => String) { if (l.isInfoEnabled) l.info(msg) }
  def info(msgFmt: String, a: => Any) { if (l.isInfoEnabled) l.info(format(msgFmt, &(a))) }
  def info(msgFmt: String, a: => Any, b: => Any) { if (l.isInfoEnabled) l.info(format(msgFmt, &(a), &(b))) }
  def info(msgFmt: String, a: => Any, b: => Any, c: => Any) { if (l.isInfoEnabled) l.info(format(msgFmt, &(a), &(b), &(c))) }

  def warn(msg: => String) { if (l.isWarnEnabled) l.warn(msg) }
  def warn(msgFmt: String, a: => Any) { if (l.isWarnEnabled) l.warn(format(msgFmt, &(a))) }
  def warn(msgFmt: String, a: => Any, b: => Any) { if (l.isWarnEnabled) l.warn(format(msgFmt, &(a), &(b))) }
  def warn(msgFmt: String, a: => Any, b: => Any, c: => Any) { if (l.isWarnEnabled) l.warn(format(msgFmt, &(a), &(b), &(c))) }

  def error(msg: => String) { if (l.isErrorEnabled) l.error(msg) }
  def error(msgFmt: String, a: => Any) { if (l.isErrorEnabled) l.error(format(msgFmt, &(a))) }
  def error(msgFmt: String, a: => Any, b: => Any) { if (l.isErrorEnabled) l.error(format(msgFmt, &(a), &(b))) }
  def error(msgFmt: String, a: => Any, b: => Any, c: => Any) { if (l.isErrorEnabled) l.error(format(msgFmt, &(a), &(b), &(c))) }
  def error(cause: Throwable, msg: => String) { if (l.isErrorEnabled) l.error(msg, cause) }
  def error(cause: Throwable, msgFmt: String, a: => Any) { if (l.isErrorEnabled) l.error(format(msgFmt, &(a)), cause) }
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any) { if (l.isErrorEnabled) l.error(format(msgFmt, &(a), &(b)), cause) }
  def error(cause: Throwable, msgFmt: String, a: => Any, b: => Any, c: => Any) { if (l.isErrorEnabled) l.error(format(msgFmt, &(a), &(b), &(c)), cause) }

  @inline
  private def &(a: Any) = a.asInstanceOf[AnyRef]
}