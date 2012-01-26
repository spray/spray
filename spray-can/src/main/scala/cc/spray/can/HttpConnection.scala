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

package cc.spray.can

import akka.actor.ActorRef
import akka.dispatch.Future
import model.HttpHeader

/**
 * An instance of this trait represents an HTTP connection to a specific host and port.
 * It is returned by the [[cc.spray.can.HttpClient]] actor as a reply to a [[cc.spray.can.Connect]] message.
 */
trait HttpConnection {
  /**
   * Sends the given request and delivers its reponse as a `Future`.
   * If the response is chunked the message entities are buffered and assembled into an [[cc.spray.can.HttpResponse]]
   * by an automatically started [[cc.spray.can.DefaultReceiverActor]] before delivery.
   * If the configured maximum content length is exceeded during buffering the `Future` will be completed with a
   * respective [[cc.spray.can.HttpClientException]].
   */
  def send(request: HttpRequest): Future[HttpResponse]

  /**
   * Sends the given request and delivers its response (parts) to the given receiver actor.
   * If the response is chunked the individual response parts are delivered to the receiver actor one by one,
   * directly upon arrival, as [[cc.spray.can.MessageChunk]] and [[cc.spray.can.ChunkedResponseEnd]] messages.
   * If a context object is given all messages to the receiver actor will be a `Tuple2[<message>, <context>]`.
   * This way, you can establish the relationship between outgoing requests and incoming responses with any custom
   * marker object. All potentially occuring errors are delivered to the receiver actor as
   * [[cc.spray.can.HttpClientException]] messages.
   */
  def sendAndReceive(request: HttpRequest, receiver: ActorRef, context: Option[Any] = None)

  /**
   * Sends the given [[cc.spray.can.HttpRequest]] as the start of a chunked request. If the given request has a
   * non-empty body this body will be send immediately after the HTTP headers as the first chunk.
   * The returned [[cc.spray.can.ChunkedRequester]] allows for the sending of all subsequent chunks as well as the
   * finalization of the request.
   */
  def startChunkedRequest(request: HttpRequest): ChunkedRequester

  /**
   * Triggers the closing of the connection.
   */
  def close()
}

/**
 * An instance of this trait is returned by the `startChunkedRequest` method of an [[cc.spray.can.HttpConnection]]
 * instance. It facilitates the sending of all requests chunks as well the finalization of the request.
 */
trait ChunkedRequester {
  /**
   * Triggers the sending of the given request chunk.
   */
  def sendChunk(chunk: MessageChunk)

  /**
   * Finalizes the request and returns a `Future` to its response. Chunked responses are handled in analogy to the
   * `send` method of the [[cc.spray.can.HttpConnection]].
   */
  def close(extensions: List[ChunkExtension] = Nil, trailer: List[HttpHeader] = Nil): Future[HttpResponse]

  /**
   * Finalizes the request and delivers the response (parts) to the given receiver actor.
   * Chunked responses are handled in analogy to the `sendAndReceive` method of the [[cc.spray.can.HttpConnection]].
   */
  def closeAndReceive(receiver: ActorRef, context: Option[Any] = None, extensions: List[ChunkExtension] = Nil,
                      trailer: List[HttpHeader] = Nil)
}






