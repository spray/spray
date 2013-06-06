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

package spray.httpx.marshalling

import akka.dispatch.{ ExecutionContext, Future }
import akka.actor.{ ActorRef, Actor, Props, ActorRefFactory }
import akka.io.Tcp
import spray.http._

trait MetaMarshallers {

  implicit def optionMarshaller[T](implicit m: Marshaller[T]) =
    Marshaller[Option[T]] { (value, ctx) ⇒
      value match {
        case Some(v) ⇒ m(v, ctx)
        case None    ⇒ ctx.marshalTo(EmptyEntity)
      }
    }

  implicit def eitherMarshaller[A, B](implicit ma: Marshaller[A], mb: Marshaller[B]) =
    Marshaller[Either[A, B]] { (value, ctx) ⇒
      value match {
        case Left(a)  ⇒ ma(a, ctx)
        case Right(b) ⇒ mb(b, ctx)
      }
    }

  implicit def futureMarshaller[T](implicit m: Marshaller[T], ec: ExecutionContext) =
    Marshaller[Future[T]] { (value, ctx) ⇒
      value.onComplete {
        case Right(v)    ⇒ m(v, ctx)
        case Left(error) ⇒ ctx.handleError(error)
      }
    }

  implicit def tryMarshaller[T](implicit m: Marshaller[T]) =
    Marshaller[Either[Throwable, T]] { (value, ctx) ⇒
      value match {
        case Right(v) ⇒ m(v, ctx)
        case Left(t)  ⇒ ctx.handleError(t)
      }
    }

  implicit def streamMarshaller[T](implicit marshaller: Marshaller[T], refFactory: ActorRefFactory) =
    Marshaller[Stream[T]] { (value, ctx) ⇒
      refFactory.actorOf(Props(new MetaMarshallers.ChunkingActor(marshaller, ctx))) ! value
    }
}

object MetaMarshallers extends MetaMarshallers {
  private case class SentOk(remaining: Stream[_])

  class ChunkingActor[T](marshaller: Marshaller[T], ctx: MarshallingContext) extends Actor {
    var connectionActor: ActorRef = _

    def receive = {

      case current #:: rest ⇒
        val chunkingCtx = new DelegatingMarshallingContext(ctx) {
          override def marshalTo(entity: HttpEntity) {
            if (connectionActor == null) connectionActor = ctx.startChunkedMessage(entity, Some(SentOk(rest)))
            else connectionActor ! MessageChunk(entity.buffer).withAck(SentOk(rest))
          }
          override def handleError(error: Throwable) {
            context.stop(self)
            ctx.handleError(error)
          }
          override def startChunkedMessage(entity: HttpEntity, sentAck: Option[Any])(implicit sender: ActorRef) =
            sys.error("Cannot marshal a stream of streams")
        }
        marshaller(current.asInstanceOf[T], chunkingCtx)

      case SentOk(remaining) ⇒
        if (remaining.isEmpty) {
          connectionActor ! ChunkedMessageEnd()
          context.stop(self)
        } else self ! remaining

      case _: Tcp.ConnectionClosed ⇒
        context.stop(self)
    }
  }

}
