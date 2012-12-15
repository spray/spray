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

package spray.util

import akka.event.{Logging, LoggingAdapter}
import akka.actor._


/**
 * A LoggingAdapter that can be implicitly supplied from an implicitly available
 * ActorRefFactory (i.e. ActorSystem or ActorContext).
 * Also, it supports optional reformating of ActorPath strings from slash-separated
 * to dot-separated, which opens them up to the hierarchy-based logger configuration
 * of frameworks like logback or log4j.
 */
trait LoggingContext extends LoggingAdapter

object LoggingContext extends LoggingContextLowerOrderImplicit1 {
  implicit def fromAdapter(implicit la: LoggingAdapter) = new LoggingContext {
    def isErrorEnabled = la.isErrorEnabled
    def isWarningEnabled = la.isWarningEnabled
    def isInfoEnabled = la.isInfoEnabled
    def isDebugEnabled = la.isDebugEnabled

    protected def notifyError(message: String) { la.error(message) }
    protected def notifyError(cause: Throwable, message: String) { la.error(cause, message) }
    protected def notifyWarning(message: String) { la.warning(message) }
    protected def notifyInfo(message: String) { la.info(message) }
    protected def notifyDebug(message: String) { la.debug(message) }
  }
}

private[util] sealed abstract class LoggingContextLowerOrderImplicit1 extends LoggingContextLowerOrderImplicit2 {
  this: LoggingContext.type =>

  implicit def fromActorRefFactory(implicit refFactory: ActorRefFactory) =
    refFactory match {
      case x: ActorSystem => fromAdapter(x.log)
      case x: ActorContext => fromAdapter(actorRefLogging(x.system, x.self.path.toString))
    }

  private val actorRefLogging: (ActorSystem, String) => LoggingAdapter =
    (UtilSettings.LogActorPathsWithDots, UtilSettings.LogActorSystemName) match {
      case (false, false) => (system, path) => Logging(system.eventStream, path)
      case (false, true)  => (system, path) => Logging(system, path)
      case (true , false) => (system, path) => Logging(system.eventStream, dotify(path))
      case (true , true)  => (system, path) => Logging(system.eventStream, system.toString + '.' + dotify(path))
    }

  // drop the `akka://` prefix and replace slashes with dots
  private def dotify(path: String) = path.substring(7).replace('/', '.')
}

private[util] sealed abstract class LoggingContextLowerOrderImplicit2 {
  this: LoggingContext.type =>

  implicit val NoLogging = fromAdapter(akka.spray.NoLogging)
}

/**
 * Trait that can be mixed into an Actor to easily obtain a reference to a spray-level logger,
 * which is available under the name "log".
 */
trait SprayActorLogging { this: Actor ⇒
  val log: LoggingAdapter = implicitly[LoggingContext]
}
