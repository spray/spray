/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

package spray.can.server

import akka.util.Unsafe
import akka.spray.{ RefUtils, UnregisteredActorRef }
import akka.actor._
import spray.io.{ CommandWrapper, Command }
import spray.http._
import spray.can.Http
import spray.can.Http.RegisterChunkHandler

private object ResponseReceiverRef {
  private val responseStateOffset = Unsafe.instance.objectFieldOffset(
    classOf[ResponseReceiverRef].getDeclaredField("_responseStateDoNotCallMeDirectly"))

  sealed trait ResponseState
  case object WaitingForChunkHandler extends ResponseState
  case object Uncompleted extends ResponseState
  case object Completed extends ResponseState
  case object Chunking extends ResponseState
}

private class ResponseReceiverRef(openRequest: OpenRequest)
    extends UnregisteredActorRef(openRequest.context.actorContext) {
  import ResponseReceiverRef._

  @volatile private[this] var _responseStateDoNotCallMeDirectly: ResponseState =
    if (openRequest.isWaitingForChunkHandler) WaitingForChunkHandler else Uncompleted

  def handle(message: Any)(implicit sender: ActorRef) {
    require(sender == null || RefUtils.isLocal(sender), "A request cannot be completed from a remote actor")
    message match {
      case part: HttpMessagePartWrapper if part.messagePart.isInstanceOf[HttpResponsePart] ⇒
        part.messagePart.asInstanceOf[HttpResponsePart] match {
          case x: HttpResponse ⇒
            require(x.protocol == HttpProtocols.`HTTP/1.1`, "Response must have protocol HTTP/1.1")
            dispatch(part, Uncompleted, Completed)
          case x: ChunkedResponseStart ⇒
            require(x.response.protocol == HttpProtocols.`HTTP/1.1`, "Response must have protocol HTTP/1.1")
            dispatch(part, Uncompleted, Chunking)
          case _: MessageChunk      ⇒ dispatch(part, Chunking, Chunking)
          case _: ChunkedMessageEnd ⇒ dispatch(part, Chunking, Completed)

        }
      case RegisterChunkHandler(handler) ⇒ dispatch(ChunkHandlerRegistration(openRequest, handler), WaitingForChunkHandler, Uncompleted)
      case s: SetRequestTimeout          ⇒ dispatch(CommandWrapper(s))
      case s: SetTimeoutTimeout          ⇒ dispatch(CommandWrapper(s))
      case x: Command                    ⇒ dispatch(x)
      case x ⇒
        openRequest.context.log.warning("Illegal response {} to {}", x, requestInfo)
        unhandledMessage(x)
    }
  }

  private def dispatch(msg: HttpMessagePartWrapper, expectedState: ResponseState, newState: ResponseState)(implicit sender: ActorRef): Unit =
    dispatch(new Response(openRequest, Http.MessageCommand(msg)))
  private def dispatch(cmd: Command, expectedState: ResponseState, newState: ResponseState)(implicit sender: ActorRef): Unit = {
    if (Unsafe.instance.compareAndSwapObject(this, responseStateOffset, expectedState, newState)) {
      dispatch(cmd)
    } else {
      openRequest.context.log.warning("Cannot dispatch {} for {} since current response state is " +
        "'{}' but should be '{}'", cmd.getClass.getSimpleName, requestInfo,
        Unsafe.instance.getObjectVolatile(this, responseStateOffset), expectedState)
      unhandledMessage(cmd)
    }
  }

  private def dispatch(cmd: Command)(implicit sender: ActorRef) {
    val ac = openRequest.context.actorContext
    if (ac != null) ac.self ! cmd
  }

  private def unhandledMessage(message: Any)(implicit sender: ActorRef): Unit = {
    val ac = openRequest.context.actorContext
    if (ac != null) ac.system.eventStream.publish(UnhandledMessage(message, sender, this))
  }

  private def requestInfo = openRequest.request.method.toString + " request to '" + openRequest.request.uri + '\''
}

private case class Response(openRequest: OpenRequest, cmd: Command) extends Command
private case class ChunkHandlerRegistration(openRequest: OpenRequest, chunkHandler: ActorRef) extends Command()
