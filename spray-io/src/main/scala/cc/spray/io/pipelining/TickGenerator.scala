/*
 * Copyright (C) 2011-2012 spray.cc
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
package pipelining

import akka.util.Duration

object TickGenerator {

  def apply(period: Duration): PipelineStage = {
    require(period.isFinite, "period must not be infinite")
    require(period > Duration.Zero, "period must be positive")

    new EventPipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): EPL = {
        val generator = context.connectionActorContext.system.scheduler.schedule(
          initialDelay = period,
          frequency = period,
          receiver = context.self,
          message = Tick
        )
        _ match {
          case x: IoPeer.Closed =>
            generator.cancel()
            eventPL(x)
          case x => eventPL(x)
        }
      }
    }
  }

  ////////////// COMMANDS //////////////
  case object Tick extends Event with Droppable
}