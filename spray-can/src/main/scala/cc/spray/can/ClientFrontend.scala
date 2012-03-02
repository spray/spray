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

package cc.spray.can

import model._
import cc.spray.io._
import akka.event.LoggingAdapter
import akka.actor.ActorRef

object ClientFrontend {

  def apply(log: LoggingAdapter) = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = new Pipelines {
      var lastCommandSender: ActorRef = null

      def commandPipeline(command: Command) {
        lastCommandSender = context.connectionActorContext.sender
        commandPL(command)
      }

      def eventPipeline(event: Event) {
        event match {
          case x: HttpResponsePart => dispatch(x)
          case x: HttpClient.SendCompleted => dispatch(x)
          case x: HttpClient.Closed => dispatch(x)
          case ev => eventPL(ev)
        }
      }

      def dispatch(msg: Any) {
        if (lastCommandSender != null) commandPL(IoPeer.Dispatch(lastCommandSender, msg))
      }
    }
  }

}