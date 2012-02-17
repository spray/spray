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

import akka.actor.{ActorContext, ActorRef, Props}


sealed trait MessageHandler
case class SingletonHandler(handler: ActorRef) extends MessageHandler
case class PerConnectionHandler(handlerProps: Props) extends MessageHandler
case class PerMessageHandler(handlerProps: Props) extends MessageHandler

object MessageHandler {
  ////////////// COMMANDS //////////////
  sealed trait DispatchCommand extends Command
  case class DispatchMessage(message: Any) extends DispatchCommand
  case class DispatchMessagePart(messagePart: Any) extends DispatchCommand
}

object MessageHandlerDispatch {
  import MessageHandler._

  def apply(messageHandler: MessageHandler) = new CommandPipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command]) = {

      val dispatcher: DispatchCommand => IoPeer.Dispatch = messageHandler match {
        case SingletonHandler(handler) =>
          IoPeer.Dispatch(handler, _)

        case PerConnectionHandler(props) =>
          val handler = context.actorOf(props)
          IoPeer.Dispatch(handler, _)

        case PerMessageHandler(props) => {
          var handler: ActorRef = null
          _ match {
            case x: DispatchMessage =>
              handler = context.actorOf(props)
              IoPeer.Dispatch(handler, x)
            case x: DispatchMessagePart =>
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

}