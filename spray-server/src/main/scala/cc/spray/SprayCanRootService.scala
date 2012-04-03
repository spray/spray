/*
 * Copyright (C) 2011 Mathias Doenitz
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

import akka.actor.ActorRef
import can.HttpServer
import can.model.ChunkedMessageEnd
import http._
import io.ConnectionClosedReason
import typeconversion.ChunkSender
import SprayCanConversions._
import akka.dispatch.{Promise, Future}
import akka.util.Duration
import util.Spray

/**
 * A specialized [[cc.spray.RootService]] for connector-less deployment on top of the ''spray-can'' `HttpServer`.
 */
class SprayCanRootService(firstService: ActorRef, moreServices: ActorRef*)
        extends RootService(firstService, moreServices: _*) {
  import SprayServerSettings._

  protected override def receive = {
    case request: can.model.HttpRequest => {
      try {
        handler(contextForSprayCanRequest(request))
      } catch handleExceptions(request)
    }
    case HttpServer.RequestTimeout(request) => {
      try {
        if (TimeoutActorPath == "")
          sender ! toSprayCanResponse(timeoutResponse(fromSprayCanRequest(request)))
        else
          context.actorFor(TimeoutActorPath) ! Timeout(contextForSprayCanRequest(request))
      } catch handleExceptions(request)
    }
  }

  protected def handleExceptions(request: can.model.HttpRequest): PartialFunction[Throwable, Unit] = {
    case e: Exception => sender ! toSprayCanResponse(responseForException(request, e))
  }

  protected def contextForSprayCanRequest(request: can.model.HttpRequest) = {
    RequestContext(
      request = fromSprayCanRequest(request),
      remoteHost = "127.0.0.1", // TODO: extract from X-Remote-Addr header (see issue #95)
      responder = sprayCanAdapterResponder(sender: ActorRef)
    )
  }

  protected val chunkTimeout = akka.util.Timeout(Duration("5 s"))

  protected def sprayCanAdapterResponder(client: ActorRef): RequestResponder = {
    RequestResponder(
      complete = response => client ! toSprayCanResponse(response),
      startChunkedResponse = { response =>
        client ! toSprayCanResponse(response)
        new ChunkSender {
          def sendChunk(chunk: MessageChunk): Future[Unit] = {
            import akka.pattern.ask
            implicit def executor = context.dispatcher
            client.ask(toSprayCanMessageChunk(chunk))(chunkTimeout).flatMap[Unit] {
              case _: HttpServer.SendCompleted => Promise.successful(())
              case HttpServer.Closed(_, reason) => Promise.failed(new ClientClosedConnectionException(reason))
            }
          }
          def close(extensions: List[ChunkExtension], trailer: List[HttpHeader]) {
            client ! ChunkedMessageEnd(extensions.map(toSprayCanChunkExtension), trailer.map(toSprayCanHeader))
          }
        }
      },
      resetConnectionTimeout = () => client ! HttpServer.SetIdleTimeout(Duration.Zero)
    )
  }
}

class ClientClosedConnectionException(reason: ConnectionClosedReason) extends RuntimeException(reason.toString)