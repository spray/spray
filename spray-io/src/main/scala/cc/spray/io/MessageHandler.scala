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

import akka.actor.ActorRef


sealed trait MessageHandler extends (PipelineContext => () => ActorRef)

case class SingletonHandler(handler: ActorRef) extends MessageHandler {
  def apply(ctx: PipelineContext) = () => handler
}

case class PerConnectionHandler(handlerCreator: PipelineContext => ActorRef) extends MessageHandler {
  def apply(ctx: PipelineContext) = {
    val handler = handlerCreator(ctx)
    () => handler
  }
}

case class PerMessageHandler(handlerCreator: PipelineContext => ActorRef) extends MessageHandler {
  def apply(ctx: PipelineContext) = () => handlerCreator(ctx)
}