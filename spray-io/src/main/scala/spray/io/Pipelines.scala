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

import akka.actor.{ActorRef, ActorContext}

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
  def self: ActorRef = connectionActorContext.self
  def sender: ActorRef = connectionActorContext.sender
}

class DefaultPipelineContext(val connection: Connection,
                             val connectionActorContext: ActorContext) extends PipelineContext

trait PipelineStage { left =>
  type CPL = Pipeline[Command]  // alias for brevity
  type EPL = Pipeline[Event]    // alias for brevity

  def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines

  def >> (right: PipelineStage): PipelineStage =
    if (right == EmptyPipelineStage) this
    else new PipelineStage {
      def build(ctx: PipelineContext, cpl: CPL, epl: EPL) = {
        var cplProxy: CPL = Pipeline.Uninitialized
        var eplProxy: EPL = Pipeline.Uninitialized
        val cplProxyPoint: CPL = cplProxy(_)
        val eplProxyPoint: EPL = eplProxy(_)
        val leftPL = left.build(ctx, cplProxyPoint, epl)
        val rightPL = right.build(ctx, cpl, eplProxyPoint)
        cplProxy = rightPL.commandPipeline
        eplProxy = leftPL.eventPipeline
        Pipelines(
          commandPL = (if (leftPL.commandPipeline == cplProxyPoint) rightPL else leftPL).commandPipeline,
          eventPL = (if (rightPL.eventPipeline == eplProxyPoint) leftPL else rightPL).eventPipeline
        )
      }
    }
}

object EmptyPipelineStage extends PipelineStage {

  def build(ctx: PipelineContext, cpl: CPL, epl: EPL) = Pipelines(cpl, epl)

  override def >> (right: PipelineStage) = right
}