/*
 * Copyright (C) 2011-2012 spray.cc
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
package pipelining

import akka.actor.{ActorRef, Props}


trait MessageHandlerDispatch {
  import MessageHandlerDispatch._

  def messageHandlerCreator(messageHandler: MessageHandler, context: PipelineContext): () => ActorRef = {
    messageHandler match {
      case SingletonHandler(handler) => () => handler

      case PerConnectionHandler(handlerPropsCreator) =>
        val props = handlerPropsCreator(context.handle)
        val handler = context.connectionActorContext.actorOf(props)
        () => handler

      case PerMessageHandler(handlerPropsCreator) => () =>
        val props = handlerPropsCreator(context.handle)
        context.connectionActorContext.actorOf(props)
    }
  }

}

object MessageHandlerDispatch {
  sealed trait MessageHandler
  case class SingletonHandler(handler: ActorRef) extends MessageHandler
  case class PerConnectionHandler(handlerPropsCreator: Handle => Props) extends MessageHandler
  case class PerMessageHandler(handlerPropsCreator: Handle => Props) extends MessageHandler
}