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
        handler(fromSprayCanContext(request, remoteAddress, Right(responder)))
      } catch handleExceptions(request, responder.complete)
    }
    case can.Timeout(method, uri, protocol, headers, remoteAddress, complete) => {
      val request = can.HttpRequest(method, uri, headers)
      try {
        if (self == timeoutActor)
          complete(toSprayCanResponse(timeoutResponse(fromSprayCanRequest(request))))
        else
          timeoutActor ! Timeout(fromSprayCanContext(request, remoteAddress, Left(complete)))
      } catch handleExceptions(request, complete)
    }
  }

  protected def handleExceptions(request: can.HttpRequest,
                                 complete: can.HttpResponse => Unit): PartialFunction[Throwable, Unit] = {
    case e: Exception => complete(toSprayCanResponse(responseForException(request, e)))
  }

  protected def fromSprayCanContext(request: can.HttpRequest, remoteAddress: InetAddress,
                                    responder: Either[can.HttpResponse => Unit, can.RequestResponder]) = {
    RequestContext(
      request = fromSprayCanRequest(request),
      remoteHost = HttpIp(remoteAddress),
      responder = responder match {
        case Right(canResponder) => fromSprayCanResponder(canResponder)
        case Left(canComplete) => new SimpleResponder(fromSprayCanComplete(canComplete))
      }
    )
  }

  protected def fromSprayCanResponder(canResponder: can.RequestResponder) =
    new SprayCanAdapterResponder(canResponder, fromSprayCanComplete(canResponder.complete))

  protected def fromSprayCanComplete(complete: can.HttpResponse => Unit): RoutingResult => Unit = {
    case Respond(response) => complete(toSprayCanResponse(response))
    case _: Reject => throw new IllegalStateException
  }
}

class SprayCanAdapterResponder(canResponder: can.RequestResponder, val reply: RoutingResult => Unit)
  extends RequestResponder {

  override def startChunkedResponse(response: HttpResponse) = new ChunkedResponder {
    val canChunkedResponder = canResponder.startChunkedResponse(toSprayCanResponse(response))
    def sendChunk(chunk: MessageChunk) = canChunkedResponder.sendChunk(toSprayCanMessageChunk(chunk))
    def close(extensions: List[ChunkExtension], trailer: List[HttpHeader]) {
      canChunkedResponder.close(extensions.map(toSprayCanChunkExtension), trailer.map(toSprayCanHeader))
    }
    def onChunkSent(callback: Long => Unit) = { canChunkedResponder.onChunkSent(callback); this }
  }

  override def onClientClose(callback: () => Unit) = { canResponder.onClientClose(callback); this }

  override def resetConnectionTimeout() { canResponder.resetConnectionTimeout() }

  def withReply(newReply: RoutingResult => Unit) = new SprayCanAdapterResponder(canResponder, newReply)
}