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

package spray

import akka.actor.ActorRef
import akka.io.Tcp

package object io {
  type Pipeline[-T] = T ⇒ Unit
  object Pipeline {
    val Uninitialized: Pipeline[Any] = _ ⇒ throw new RuntimeException("Pipeline not yet initialized")

    case class Tell(receiver: ActorRef, message: Any, sender: ActorRef) extends Command

    case class ActorDeath(actor: ActorRef) extends Event
    case class AckEvent(ack: Any) extends Event
  }
  type Command = Tcp.Command
  type Event = Tcp.Event
  type PipelineStage = RawPipelineStage[PipelineContext]

  implicit def pimpBooleanWithOptionalPipelineStageOperator(condition: Boolean) = new PimpedBoolean(condition)
  class PimpedBoolean(condition: Boolean) {
    // unfortunately we cannot use the nicer right-associative `?:` operator due to
    // https://issues.scala-lang.org/browse/SI-1980
    def ?[T <: PipelineContext](stage: ⇒ RawPipelineStage[T]) = if (condition) stage else EmptyPipelineStage
  }
}

package io {
  trait Droppable // marker for Commands and Events

  case class CommandWrapper(command: AnyRef) extends Command
}
