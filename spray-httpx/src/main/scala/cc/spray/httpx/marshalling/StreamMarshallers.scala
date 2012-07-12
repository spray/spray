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
import cc.spray.http._


trait StreamMarshallers {

  implicit def streamMarshaller[T](implicit marshaller: Marshaller[T],
                                   refFactory: ActorRefFactory): Marshaller[Stream[T]] = new Marshaller[Stream[T]] {

    def apply(selector: ContentTypeSelector) = marshaller(selector).right.map { marshalling =>
      new Marshalling[Stream[T]] {
        def apply(value: Stream[T], ctx: MarshallingContext) {
          refFactory.actorOf(Props(new ChunkingActor(ctx, marshalling))) ! value
        }
      }
    }
  }

}

class ChunkingActor[T](ctx: MarshallingContext, marshalling: Marshalling[T]) extends Actor {
  var connectionActor: ActorRef = _
  var remaining: Stream[_] = _

  def receive = {

    case current #:: rest =>
      val chunkingCtx = new MarshallingContext {
        def marshalTo(entity: HttpEntity) {
          if (connectionActor == null) connectionActor = ctx.startChunkedMessage(entity)
          connectionActor ! MessageChunk(entity.buffer)
        }
        def handleError(error: Throwable) {
          context.stop(self)
          ctx.handleError(error)
        }
        def startChunkedMessage(entity: HttpEntity) = sys.error("Cannot marshal a stream of streams")
      }
      marshalling.runSafe(current.asInstanceOf[T], chunkingCtx)
      remaining = rest

    case x if x.getClass.getName == "cc.spray.io.IoWorker$AckSend$" =>
      assert(remaining != null, "Unmatched AckSend")
      if (remaining.isEmpty) {
        connectionActor ! ChunkedMessageEnd()
        context.stop(self)
      } else self ! remaining

    case x if x.getClass.getName == "cc.spray.io.IoWorker$Closed$" =>
      context.stop(self)
  }
}