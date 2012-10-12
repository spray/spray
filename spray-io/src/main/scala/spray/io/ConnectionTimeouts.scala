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

import akka.util.Duration
import akka.event.LoggingAdapter
import spray.util.IdleTimeout


object ConnectionTimeouts {

  def apply(idleTimeout: Long, log: LoggingAdapter): PipelineStage = {
    require(idleTimeout >= 0)

    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
        var timeout = idleTimeout
        var lastActivity = System.currentTimeMillis

        val commandPipeline: CPL = {
          case x: SetIdleTimeout =>
            timeout = x.timeout.toMillis

          case x: IOPeer.Send =>
            commandPL(x)
            lastActivity = System.currentTimeMillis

          case cmd => commandPL(cmd)
        }

        val eventPipeline: EPL = {
          case x: IOPeer.Received =>
            lastActivity = System.currentTimeMillis
            eventPL(x)

          case TickGenerator.Tick =>
            if (timeout > 0 && (lastActivity + timeout < System.currentTimeMillis)) {
              log.debug("Closing connection due to idle timeout...")
              commandPL(IOPeer.Close(IdleTimeout))
            }
            eventPL(TickGenerator.Tick)

          case ev => eventPL(ev)
        }
      }
    }
  }

  ////////////// COMMANDS //////////////

  case class SetIdleTimeout(timeout: Duration) extends Command {
    require(timeout.finite_?, "timeout must not be infinite, set to zero to disable")
    require(timeout >= Duration.Zero, "timeout must not be negative")
  }
}