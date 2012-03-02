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
package pipelines

import akka.actor.{ActorRef, Props}
import java.nio.channels.SocketChannel

object MessageHandlerDispatch {

  def apply(messageHandler: MessageHandler) = new CommandPipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {

      val dispatcher: DispatchCommand => IoPeer.Dispatch = messageHandler match {
        case SingletonHandler(handler) =>
          cmd => IoPeer.Dispatch(handler, cmd.message)

        case PerConnectionHandler(handlerPropsCreator) =>
          val props = handlerPropsCreator(context.handle)
          val handler = context.connectionActorContext.actorOf(props)
          cmd => IoPeer.Dispatch(handler, cmd.message)

        case PerMessageHandler(handlerPropsCreator) => {
          var handler: ActorRef = null
          _ match {
            case x: DispatchNewMessage =>
              val props = handlerPropsCreator(context.handle)
              handler = context.connectionActorContext.actorOf(props)
              IoPeer.Dispatch(handler, x)
            case x: DispatchFollowupMessage =>
              if (handler == null) throw new IllegalStateException // a MessagePart without a preceding Message?
              IoPeer.Dispatch(handler, x)
          }
        }
      }

      _ match {
        case x: DispatchCommand => commandPL(dispatcher(x))
        case cmd => commandPL(cmd)
      }
    }
  }

  sealed trait MessageHandler
  case class SingletonHandler(handler: ActorRef) extends MessageHandler
  case class PerConnectionHandler(handlerPropsCreator: Handle => Props) extends MessageHandler
  case class PerMessageHandler(handlerPropsCreator: Handle => Props) extends MessageHandler

  ////////////// COMMANDS //////////////
  sealed trait DispatchCommand extends Command {
    def message: Any
  }
  case class DispatchNewMessage(message: Any) extends DispatchCommand
  case class DispatchFollowupMessage(message: Any) extends DispatchCommand
}