/*
 * Copyright (C) 2011-2013 spray.io
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

import akka.util.duration._
import akka.io.Tcp
import akka.util.{ FiniteDuration, Duration }
import spray.util._

//# source-quote
object TickGenerator {

  def apply(period: Duration): PipelineStage = {
    requirePositiveOrUndefined(period)

    new OptionalPipelineStage[PipelineContext] {

      def enabled(context: PipelineContext): Boolean = period.isFinite()

      def applyIfEnabled(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var next = scheduleNext()

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case Tick                    ⇒ next = scheduleNext(); eventPL(Tick)
            case x: Tcp.ConnectionClosed ⇒ next.cancel(); eventPL(x)
            case x                       ⇒ eventPL(x)
          }

          def scheduleNext() = {
            implicit val executionContext = context.dispatcher
            context.system.scheduler.scheduleOnce(period.asInstanceOf[FiniteDuration], context.self, Tick)
          }
        }
    }
  }

  ////////////// COMMANDS //////////////
  case object Tick extends Event with Droppable
}
//#
