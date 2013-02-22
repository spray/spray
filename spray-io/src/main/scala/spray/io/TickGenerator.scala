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

import scala.concurrent.duration._
import akka.io.Tcp

//# source-quote
// TODO: change to only schedule the Tick once and reschedule on Tick reception
object TickGenerator {

  def apply(period: Duration): PipelineStage = {
    require(period > Duration.Zero, "period must be > 0")

    new OptionalPipelineStage[PipelineContext] {

      def enabled(context: PipelineContext): Boolean = period.isFinite()

      def applyIfEnabled(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          val generator =
            context.system.scheduler.schedule(
              initialDelay = period.asInstanceOf[FiniteDuration],
              interval = period.asInstanceOf[FiniteDuration],
              receiver = context.self,
              message = Tick)(context.dispatcher)

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case x: Tcp.ConnectionClosed ⇒
              generator.cancel()
              eventPL(x)
            case x ⇒ eventPL(x)
          }
        }
    }
  }

  ////////////// COMMANDS //////////////
  case object Tick extends Event with Droppable
}
//#