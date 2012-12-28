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

import java.util.concurrent.TimeUnit._
import scala.concurrent.duration.{FiniteDuration, Duration}


//# source-quote
object TickGenerator {

  def apply(millis: Long): PipelineStage = apply(Duration(millis, MILLISECONDS))

  def apply(period: FiniteDuration): PipelineStage = {
    require(period > Duration.Zero, "period must be positive")

    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          val generator = {
            val system = context.connectionActorContext.system
            system.scheduler.schedule(
              initialDelay = period,
              interval = period,
              receiver = context.self,
              message = Tick
            )(system.dispatcher)
          }

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case x: IOConnectionActor.Closed =>
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
//#