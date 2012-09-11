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

package cc.spray.httpx.marshalling

import akka.actor._
import cc.spray.util.model.{IOClosed, IOSent}
import cc.spray.http._


trait StreamMarshallers {

  implicit def streamMarshaller[T](implicit marshaller: Marshaller[T], refFactory: ActorRefFactory) =
    Marshaller[Stream[T]] { (value, ctx) =>
      refFactory.actorOf(Props(new StreamMarshallers.ChunkingActor(marshaller, ctx))) ! value
    }
}

object StreamMarshallers extends StreamMarshallers {

  class ChunkingActor[T](marshaller: Marshaller[T], ctx: MarshallingContext) extends Actor {
    var connectionActor: ActorRef = _
    var remaining: Stream[_] = _

    def receive = {

      case current #:: rest =>
        val chunkingCtx = new DelegatingMarshallingContext(ctx) {
          override def marshalTo(entity: HttpEntity) {
            if (connectionActor == null) connectionActor = ctx.startChunkedMessage(entity)
            else connectionActor ! MessageChunk(entity.buffer)
          }
          override def handleError(error: Throwable) {
            context.stop(self)
            ctx.handleError(error)
          }
          override def startChunkedMessage(entity: HttpEntity)(implicit sender: ActorRef) =
            sys.error("Cannot marshal a stream of streams")
        }
        marshaller(current.asInstanceOf[T], chunkingCtx)
        remaining = rest

      case _: IOSent =>
        assert(remaining != null, "Unmatched AckSend")
        if (remaining.isEmpty) {
          connectionActor ! ChunkedMessageEnd()
          context.stop(self)
        } else self ! remaining

      case _: IOClosed =>
        context.stop(self)
    }
  }
}


