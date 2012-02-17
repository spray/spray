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

package cc.spray
package can

import model._
import io._
import collection.mutable.Queue
import rendering.HttpResponsePartRenderingContext
import akka.actor.ActorContext

object ServerFrontend {

  def apply() = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = new Pipelines {
      val openRequests = Queue.empty[HttpRequest]

      def commandPipeline(command: Command) {
        commandPL {
          command match {
            case part: HttpResponsePart =>
              if (openRequests.isEmpty) throw new IllegalStateException("Received ResponsePart for non-existing request")
              val request = part match {
                case _: HttpResponse      => openRequests.dequeue()
                case _: ChunkedMessageEnd => openRequests.dequeue()
                case _                    => openRequests.front
              }
              HttpResponsePartRenderingContext(part, request.method, request.protocol, request.connectionHeader)
            case cmd => cmd
          }
        }
      }

      def eventPipeline(event: Event) {
        event match {
          case x: HttpRequest =>
            openRequests += x
            commandPL(MessageHandler.DispatchNewMessage(x))
          case x: ChunkedRequestStart =>
            openRequests += x.request
            commandPL(MessageHandler.DispatchNewMessage(x))
          case x: HttpMessagePart =>
            commandPL(MessageHandler.DispatchFollowupMessage(x))
          case x: HttpServer.SendCompleted =>
            commandPL(MessageHandler.DispatchFollowupMessage(x))
          case x: HttpServer.Closed =>
            commandPL(MessageHandler.DispatchFollowupMessage(x))
          case ev => eventPL(ev)
        }
      }
    }

  }

}