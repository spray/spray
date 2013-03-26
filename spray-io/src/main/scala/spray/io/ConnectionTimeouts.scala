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

import scala.concurrent.duration.{ Duration, FiniteDuration }
import akka.io.Tcp
import System.{ currentTimeMillis ⇒ now }

object ConnectionTimeouts {

  def apply(idleTimeout: Duration): PipelineStage = {
    require(idleTimeout > Duration.Zero, "idleTimeout must be > 0")

    new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
        var timeout = idleTimeout
        var lastActivity = now

        val commandPipeline: CPL = {
          case x: Tcp.Write ⇒
            commandPL(x)
            lastActivity = now

          case SetIdleTimeout(x) ⇒ timeout = x

          case cmd ⇒ commandPL(cmd)
        }

        val eventPipeline: EPL = {
          case x: Tcp.Received ⇒
            lastActivity = now
            eventPL(x)

          case tick@ TickGenerator.Tick ⇒
            if (timeout.isFinite && (lastActivity + timeout.toMillis < System.currentTimeMillis)) {
              context.log.debug("Closing connection due to idle timeout...")
              commandPL(Tcp.Close)
            }
            eventPL(tick)

          case ev ⇒ eventPL(ev)
        }
      }
    }
  }

  ////////////// COMMANDS //////////////

  case class SetIdleTimeout(timeout: Duration) extends Command {
    require(timeout > Duration.Zero, "timeout must be > 0")
  }
}