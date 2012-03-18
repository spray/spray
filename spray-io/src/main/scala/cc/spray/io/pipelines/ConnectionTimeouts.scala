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

package cc.spray.io
package pipelines

import akka.util.Duration
import akka.event.LoggingAdapter

object ConnectionTimeouts {

  def apply(idleTimeout: Duration, log: LoggingAdapter): PipelineStage = {
    require(idleTimeout >= Duration.Zero, "timeout must not be negative")

    new DoublePipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL) = new Pipelines {
        var timeout = idleTimeout
        var lastActivity = System.currentTimeMillis

        def commandPipeline(command: Command) {
          command match {
            case x: SetIdleTimeout => timeout = x.timeout
            case _ => commandPL(command)
          }
        }

        def eventPipeline(event: Event) {
          event match {
            case _: IoPeer.Received      => lastActivity = System.currentTimeMillis
            case _: IoPeer.SendCompleted => lastActivity = System.currentTimeMillis
            case TickGenerator.Tick      =>
              if (timeout.isFinite && (lastActivity + timeout.toMillis) < System.currentTimeMillis) {
                log.debug("Closing connection due to idle timeout...")
                commandPL(IoPeer.Close(IdleTimeout))
              }
            case _ =>
          }
          eventPL(event)
        }
      }
    }
  }

  ////////////// COMMANDS //////////////
  case class SetIdleTimeout(timeout: Duration) extends Command {
    require(timeout >= Duration.Zero, "timeout must not be negative")
  }
}