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
import http._
import typeconversion.ChunkSender
import utils.ActorHelpers
import java.net.InetAddress
import SprayCanConversions._

/**
 * A specialized [[cc.spray.RootService]] for connector-less deployment on top of the ''spray-can'' `HttpServer`.
 */
class SprayCanRootService(firstService: ActorRef, moreServices: ActorRef*)
        extends RootService(firstService, moreServices: _*) {

  lazy val timeoutActor = ActorHelpers.actor(SprayServerSettings.TimeoutActorId)

  protected override def receive = {
    case context: can.RequestContext => {
      import context._
      try {
        handler(fromSprayCanContext(request, remoteAddress, responder))
      } catch handleExceptions(request, responder.complete)
    }
    case can.Timeout(method, uri, protocol, headers, remoteAddress, complete) => {
      val request = can.HttpRequest(method, uri, headers)
      try {
        if (self == timeoutActor)
          complete(toSprayCanResponse(timeoutResponse(fromSprayCanRequest(request))))
        else
          timeoutActor ! Timeout(fromSprayCanContext(request, remoteAddress, complete))
      } catch handleExceptions(request, complete)
    }
  }

  protected def handleExceptions(request: can.HttpRequest,
                                 complete: can.HttpResponse => Unit): PartialFunction[Throwable, Unit] = {
    case e: Exception => complete(toSprayCanResponse(responseForException(request, e)))
  }

  protected def fromSprayCanContext(request: can.HttpRequest, remoteAddress: InetAddress,
                                    canComplete: can.HttpResponse => Unit) = {
    RequestContext(
      request = fromSprayCanRequest(request),
      remoteHost = HttpIp(remoteAddress),
      responder = new SimpleResponder(
        response => canComplete(toSprayCanResponse(response)),
        _ => throw new IllegalStateException
      )
    )
  }

  protected def fromSprayCanContext(request: can.HttpRequest, remoteAddress: InetAddress,
                                    responder: can.RequestResponder) = {
    RequestContext(
      request = fromSprayCanRequest(request),
      remoteHost = HttpIp(remoteAddress),
      responder = new SprayCanAdapterResponder(responder,
        response => responder.complete(toSprayCanResponse(response)),
        _ => throw new IllegalStateException
      )
    )
  }
}

class SprayCanAdapterResponder(canResponder: can.RequestResponder,
                               val complete: HttpResponse => Unit,
                               val reject: Set[Rejection] => Unit) extends RequestResponder {

  def withComplete(newComplete: HttpResponse => Unit) =
    new SprayCanAdapterResponder(canResponder, newComplete, reject)

  def withReject(newReject: Set[Rejection] => Unit) =
    new SprayCanAdapterResponder(canResponder, complete, newReject)

  def withOnClientClose(callback: () => Unit) =
    new SprayCanAdapterResponder(canResponder.withOnClientClose(callback), complete, reject)

  override def startChunkedResponse(response: HttpResponse) =
    new SprayCanAdapterChunkSender(canResponder.startChunkedResponse(toSprayCanResponse(response)))

  override def resetConnectionTimeout() { canResponder.resetConnectionTimeout() }
}

class SprayCanAdapterChunkSender(canChunkedResponder: can.ChunkedResponder) extends ChunkSender {
  def sendChunk(chunk: MessageChunk) = canChunkedResponder.sendChunk(toSprayCanMessageChunk(chunk))

  def close(extensions: List[ChunkExtension], trailer: List[HttpHeader]) {
    canChunkedResponder.close(extensions.map(toSprayCanChunkExtension), trailer.map(toSprayCanHeader))
  }

  def withOnChunkSent(callback: Long => Unit) =
    new SprayCanAdapterChunkSender(canChunkedResponder.withOnChunkSent(callback))
}