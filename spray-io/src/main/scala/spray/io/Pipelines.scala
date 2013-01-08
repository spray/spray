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

import language.experimental.macros
import scala.reflect.macros.{Context => MacroContext}
import akka.actor.{ActorRef, ActorContext}
import akka.event.LoggingAdapter


//# pipelines
trait Pipelines {
  def commandPipeline: Pipeline[Command]
  def eventPipeline: Pipeline[Event]
}
//#

object Pipelines {
  val Uninitialized = apply(Pipeline.Uninitialized, Pipeline.Uninitialized)

  def apply(commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = new Pipelines {
    val commandPipeline = commandPL
    val eventPipeline = eventPL
  }
}

object Pipeline {
  val Uninitialized: Pipeline[Any] = _ => throw new RuntimeException("Pipeline not yet initialized")
}

trait PipelineContext {
  def connection: Connection
  def connectionActorContext: ActorContext
  def log: LoggingAdapter
  def self: ActorRef = connectionActorContext.self
  def sender: ActorRef = connectionActorContext.sender
}

class DefaultPipelineContext(val connection: Connection,
                             val connectionActorContext: ActorContext,
                             val log: LoggingAdapter) extends PipelineContext

trait PipelineStage { left =>
  type CPL = Pipeline[Command]  // alias for brevity
  type EPL = Pipeline[Event]    // alias for brevity

  def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines

  def >> (right: PipelineStage): PipelineStage =
    if (right == EmptyPipelineStage) this
    else new PipelineStage {
      def apply(ctx: PipelineContext, cpl: CPL, epl: EPL) = {
        var cplProxy: CPL = Pipeline.Uninitialized
        var eplProxy: EPL = Pipeline.Uninitialized
        val cplProxyPoint: CPL = cplProxy(_)
        val eplProxyPoint: EPL = eplProxy(_)
        val leftPL = left(ctx, cplProxyPoint, epl)
        val rightPL = right(ctx, cpl, eplProxyPoint)
        cplProxy = rightPL.commandPipeline
        eplProxy = leftPL.eventPipeline
        Pipelines(
          commandPL = (if (leftPL.commandPipeline == cplProxyPoint) rightPL else leftPL).commandPipeline,
          eventPL = (if (rightPL.eventPipeline == eplProxyPoint) leftPL else rightPL).eventPipeline
        )
      }
    }

  def ? (condition: Boolean): PipelineStage = macro PipelineStage.enabled
}

object PipelineStage {
  type PipelineStageContext = MacroContext { type PrefixType = PipelineStage }
  def enabled(c: PipelineStageContext)(condition: c.Expr[Boolean]) =
    c.universe.reify {
      if (condition.splice) c.prefix.splice else EmptyPipelineStage
    }
}

trait OptionalPipelineStage extends PipelineStage {
  def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
    if (enabled(context)) applyIfEnabled(context, commandPL, eventPL)
    else Pipelines(commandPL, eventPL)

  def enabled(context: PipelineContext): Boolean

  def applyIfEnabled(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines
}

object EmptyPipelineStage extends PipelineStage {

  def apply(ctx: PipelineContext, cpl: CPL, epl: EPL) = Pipelines(cpl, epl)

  override def >> (right: PipelineStage) = right
}