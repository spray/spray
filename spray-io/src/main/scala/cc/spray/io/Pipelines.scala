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

import akka.actor.ActorContext
import java.nio.channels.SocketChannel

trait Pipelines {
  def commandPipeline(command: Command)
  def eventPipeline(event: Event)
}

object Pipelines {
  def apply(commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = new Pipelines {
    def commandPipeline(command: Command) { commandPL(command) }
    def eventPipeline(event: Event) { eventPL(event) }
  }
}

object Pipeline {
  val uninitialized: Pipeline[Any] = _ => throw new RuntimeException("Pipeline not yet initialized")
}

case class PipelineContext(channel: SocketChannel, connectionActorContext: ActorContext)

sealed trait PipelineStage {
  type PS = PipelineStage       // alias for brevity
  type CPL = Pipeline[Command]  // alias for brevity
  type EPL = Pipeline[Event]    // alias for brevity

  type BuildResult
  type Select[A <: PS, B <: PS, C <: PS, D <: PS] <: PS
  type AppendCommandStage <: PS
  type AppendEventStage <: PS
  type Stage[Next <: PS] =
    Next#Select[AppendCommandStage, AppendEventStage, DoublePipelineStage, EmptyPipelineStage.type]

  def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult

  def buildPipelines(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines

  def ~> [Next <: PS](right: Next): Stage[Next]
}

trait CommandPipelineStage extends PipelineStage { left =>
  type BuildResult = CPL
  type Select[A <: PS, B <: PS, C <: PS, D <: PS] = A
  type AppendCommandStage = CommandPipelineStage
  type AppendEventStage = DoublePipelineStage

  def buildPipelines(ctx: PipelineContext, cpl: CPL, epl: EPL) =
    Pipelines(build(ctx, cpl, epl), epl)

  def ~> [Next <: PS](right: Next) = {
    right match {
      case x: CommandPipelineStage => new CommandPipelineStage {
        def build(ctx: PipelineContext, cpl: CPL, epl: EPL) =
          left.build(ctx, x.build(ctx, cpl, epl), epl)
      }
      case x: EventPipelineStage => new DoublePipelineStage {
        def build(ctx: PipelineContext, cpl: CPL, epl: EPL) =
          Pipelines(left.build(ctx, cpl, epl), x.build(ctx, cpl, epl))
      }
      case x: DoublePipelineStage => new DoublePipelineStage {
        def build(ctx: PipelineContext, cpl: CPL, epl: EPL) = {
          val rightPL = x.build(ctx, cpl, epl)
          Pipelines(left.build(ctx, rightPL.commandPipeline, epl), rightPL.eventPipeline)
        }
      }
      case EmptyPipelineStage => this
    }
  }.asInstanceOf[Stage[Next]]
}

trait EventPipelineStage extends PipelineStage { left =>
  type BuildResult = EPL
  type Select[A <: PS, B <: PS, C <: PS, D <: PS] = B
  type AppendCommandStage = DoublePipelineStage
  type AppendEventStage = EventPipelineStage

  def buildPipelines(ctx: PipelineContext, cpl: CPL, epl: EPL) =
    Pipelines(cpl, build(ctx, cpl, epl))

  def ~> [Next <: PS](right: Next) = {
    right match {
      case x: CommandPipelineStage => new DoublePipelineStage {
        def build(ctx: PipelineContext, cpl: CPL, epl: EPL) =
          Pipelines(x.build(ctx, cpl, epl), left.build(ctx, cpl, epl))
      }
      case x: EventPipelineStage => new EventPipelineStage {
        def build(ctx: PipelineContext, cpl: CPL, epl: EPL) =
          x.build(ctx, cpl, left.build(ctx, cpl, epl))
      }
      case x: DoublePipelineStage => new DoublePipelineStage {
        def build(ctx: PipelineContext, cpl: CPL, epl: EPL) =
          x.build(ctx, cpl, left.build(ctx, cpl, epl))
      }
      case EmptyPipelineStage => this
    }
  }.asInstanceOf[Stage[Next]]
}

trait DoublePipelineStage extends PipelineStage { left =>
  type BuildResult = Pipelines
  type Select[A <: PS, B <: PS, C <: PS, D <: PS] = C
  type AppendCommandStage = DoublePipelineStage
  type AppendEventStage = DoublePipelineStage

  def buildPipelines(ctx: PipelineContext, cpl: CPL, epl: EPL) =
    build(ctx, cpl, epl)

  def ~> [Next <: PS](right: Next) = {
    right match {
      case x: CommandPipelineStage => new DoublePipelineStage {
        def build(ctx: PipelineContext, cpl: CPL, epl: EPL) =
          left.build(ctx, x.build(ctx, cpl, epl), epl)
      }
      case x: EventPipelineStage => new DoublePipelineStage {
        def build(ctx: PipelineContext, cpl: CPL, epl: EPL) = {
          val leftPL = left.build(ctx, cpl, epl)
          Pipelines(leftPL.commandPipeline, x.build(ctx, cpl, leftPL.eventPipeline))
        }
      }
      case x: DoublePipelineStage => new DoublePipelineStage {
        def build(ctx: PipelineContext, cpl: CPL, epl: EPL) = {
          var cplProxy: CPL = Pipeline.uninitialized
          var eplProxy: EPL = Pipeline.uninitialized
          val leftPL = left.build(ctx, cplProxy(_), epl)
          val rightPL = x.build(ctx, cpl, eplProxy(_))
          cplProxy = rightPL.commandPipeline
          eplProxy = leftPL.eventPipeline
          Pipelines(leftPL.commandPipeline, rightPL.eventPipeline)
        }
      }
      case EmptyPipelineStage => this
    }
  }.asInstanceOf[Stage[Next]]
}

object EmptyPipelineStage extends PipelineStage {
  type BuildResult = Pipelines
  type Select[A <: PS, B <: PS, C <: PS, D <: PS] = D
  type AppendCommandStage = CommandPipelineStage
  type AppendEventStage = EventPipelineStage

  def build(ctx: PipelineContext, cpl: CPL, epl: EPL) =
    Pipelines(cpl, epl)

  def buildPipelines(ctx: PipelineContext, cpl: CPL, epl: EPL) =
    build(ctx, cpl, epl)

  def ~> [Next <: PS](right: Next) = right.asInstanceOf[Stage[Next]]
}