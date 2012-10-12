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
import akka.actor.{ActorRefFactory, ActorContext, ActorSystem}


trait LoggingContext extends LoggingAdapter

object LoggingContext extends LoggingContextLowerOrderImplicits {

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

private[util] sealed abstract class LoggingContextLowerOrderImplicits {
  this: LoggingContext.type =>

  implicit def fromActorRefFactory(implicit refFactory: ActorRefFactory) =
    refFactory match {
      case x: ActorSystem => fromAdapter(x.log)
      case x: ActorContext => fromAdapter(Logging(x.system, x.self))
    }
}
