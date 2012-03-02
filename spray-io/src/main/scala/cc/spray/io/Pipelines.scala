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

sealed trait PipelineStage {
  type PS = PipelineStage // alias for brevity
  type BuildResult
  type Select[A <: PS, B <: PS, C <: PS, D <: PS] <: PS
  type AppendCommandStage <: PS
  type AppendEventStage <: PS
  type Stage[Next <: PS] =
    Next#Select[AppendCommandStage, AppendEventStage, DoublePipelineStage, EmptyPipelineStage.type]

  def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]): BuildResult

  def ~> [Next <: PS](right: Next): Stage[Next]
}

trait CommandPipelineStage extends PipelineStage { left =>
  type BuildResult = Pipeline[Command]
  type Select[A <: PS, B <: PS, C <: PS, D <: PS] = A
  type AppendCommandStage = CommandPipelineStage
  type AppendEventStage = DoublePipelineStage

  def ~> [Next <: PS](right: Next) = {
    right match {
      case x: CommandPipelineStage => new CommandPipelineStage {
        def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
          left.build(context, x.build(context, commandPL, eventPL), eventPL)
      }
      case x: EventPipelineStage => new DoublePipelineStage {
        def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
          Pipelines(left.build(context, commandPL, eventPL), x.build(context, commandPL, eventPL))
      }
      case x: DoublePipelineStage => new DoublePipelineStage {
        def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
          val rightPL = x.build(context, commandPL, eventPL)
          Pipelines(left.build(context, rightPL.commandPipeline, eventPL), rightPL.eventPipeline)
        }
      }
      case EmptyPipelineStage => this
    }
  }.asInstanceOf[Stage[Next]]
}

trait EventPipelineStage extends PipelineStage { left =>
  type BuildResult = Pipeline[Event]
  type Select[A <: PS, B <: PS, C <: PS, D <: PS] = B
  type AppendCommandStage = DoublePipelineStage
  type AppendEventStage = EventPipelineStage

  def ~> [Next <: PS](right: Next) = {
    right match {
      case x: CommandPipelineStage => new DoublePipelineStage {
        def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
          Pipelines(x.build(context, commandPL, eventPL), left.build(context, commandPL, eventPL))
      }
      case x: EventPipelineStage => new EventPipelineStage {
        def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
          x.build(context, commandPL, left.build(context, commandPL, eventPL))
      }
      case x: DoublePipelineStage => new DoublePipelineStage {
        def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
          x.build(context, commandPL, left.build(context, commandPL, eventPL))
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

  def ~> [Next <: PS](right: Next) = {
    right match {
      case x: CommandPipelineStage => new DoublePipelineStage {
        def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
          left.build(context, x.build(context, commandPL, eventPL), eventPL)
      }
      case x: EventPipelineStage => new DoublePipelineStage {
        def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
          val leftPL = left.build(context, commandPL, eventPL)
          Pipelines(leftPL.commandPipeline, x.build(context, commandPL, leftPL.eventPipeline))
        }
      }
      case x: DoublePipelineStage => new DoublePipelineStage {
        def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
          var cplProxy: Pipeline[Command] = Pipeline.uninitialized
          var eplProxy: Pipeline[Event] = Pipeline.uninitialized
          val leftPL = left.build(context, cplProxy(_), eventPL)
          val rightPL = x.build(context, commandPL, eplProxy(_))
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

  def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
    Pipelines(commandPL, eventPL)

  def ~> [Next <: PS](right: Next) = right.asInstanceOf[Stage[Next]]
}