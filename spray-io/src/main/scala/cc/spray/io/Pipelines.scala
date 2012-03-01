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

trait DoublePipelineStage { left =>

  def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]): Pipelines

  def ~> (right: EventPipelineStage) = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
      val leftPL = left.build(context, commandPL, eventPL)
      Pipelines(leftPL.commandPipeline, right.build(context, commandPL, leftPL.eventPipeline))
    }
  }

  def ~> (right: CommandPipelineStage) = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
      left.build(context, right.build(context, commandPL, eventPL), eventPL)
  }

  def ~> (right: DoublePipelineStage) = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
      var cplProxy: Pipeline[Command] = Pipeline.uninitialized
      var eplProxy: Pipeline[Event] = Pipeline.uninitialized
      val leftPL = left.build(context, cplProxy(_), eventPL)
      val rightPL = right.build(context, commandPL, eplProxy(_))
      cplProxy = rightPL.commandPipeline
      eplProxy = leftPL.eventPipeline
      Pipelines(leftPL.commandPipeline, rightPL.eventPipeline)
    }
  }
}


trait EventPipelineStage { left =>

  def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]): Pipeline[Event]

  def ~> (right: EventPipelineStage) = new EventPipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
      right.build(context, commandPL, left.build(context, commandPL, eventPL))
  }

  def ~> (right: CommandPipelineStage) = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
      Pipelines(right.build(context, commandPL, eventPL), left.build(context, commandPL, eventPL))
  }

  def ~> (right: DoublePipelineStage) = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
      right.build(context, commandPL, left.build(context, commandPL, eventPL))
  }

}

trait CommandPipelineStage { left =>

  def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]): Pipeline[Command]

  def ~> (right: EventPipelineStage) = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
      Pipelines(left.build(context, commandPL, eventPL), right.build(context, commandPL, eventPL))
  }

  def ~> (right: CommandPipelineStage) = new CommandPipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) =
      left.build(context, right.build(context, commandPL, eventPL), eventPL)
  }

  def ~> (right: DoublePipelineStage) = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
      val rightPL = right.build(context, commandPL, eventPL)
      Pipelines(left.build(context, rightPL.commandPipeline, eventPL), rightPL.eventPipeline)
    }
  }

}