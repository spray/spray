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

package spray.can
package client

import akka.event.LoggingAdapter
import akka.actor.{Props, ActorRef}
import spray.http.{HttpMessagePart, HttpRequestPart}
import spray.io._


/**
 * Reacts to [[spray.can.HttpClient.Connect]] messages by establishing a connection to the remote host.
 * If the connection has been established successfully a new actor is spun up for the connection, which replies to the
 * sender of the [[spray.can.HttpClient.Connect]] message with a [[spray.can.HttpClient.Connected]] message.
 *
 * You can then send [[spray.can.model.HttpRequestPart]] instances to the connection actor, which are going to be
 * replied to with [[spray.can.model.HttpResponsePart]] messages (or [[akka.actor.Status.Failure]] instances
 * in case of errors).
 */
class HttpClient(ioBridge: ActorRef,
                 settings: ClientSettings = ClientSettings())
                (implicit sslEngineProvider: ClientSSLEngineProvider) extends IOClient(ioBridge) with ConnectionActors {

  protected val pipeline: PipelineStage = HttpClient.pipeline(settings, log)

  override protected def createConnectionActor(connection: Connection): ActorRef =
    context.actorOf {
      Props {
        new IOConnectionActor(connection) {
          override def receive: Receive = super.receive orElse {
            case x: HttpMessagePart with HttpRequestPart => pipelines.commandPipeline(HttpCommand(x))
          }
        }
      }
    }
}

object HttpClient {

  private[can] def pipeline(settings: ClientSettings, log: LoggingAdapter)
                           (implicit sslEngineProvider: ClientSSLEngineProvider): PipelineStage = {
    import settings._
    ClientFrontend(RequestTimeout, log) >>
    (ResponseChunkAggregationLimit > 0) ? ResponseChunkAggregation(ResponseChunkAggregationLimit.toInt) >>
    ResponseParsing(ParserSettings, log) >>
    RequestRendering(settings) >>
    (settings.IdleTimeout > 0) ? ConnectionTimeouts(IdleTimeout, log) >>
    sslEngineProvider.createStage(log) >>
    (ReapingCycle > 0 && IdleTimeout > 0) ? TickGenerator(ReapingCycle)
  }

  /**
   * Object to be used as `tag` member of `Connect` commands in order to activate SSL encryption on the connection.
   */
  val SslEnabled = ConnectionTag(encrypted = true)

  case class ConnectionTag(encrypted: Boolean, logMarker: String = "") extends SslTlsSupport.Enabling with LogMarking {
    def encrypt(ctx: PipelineContext): Boolean = encrypted
  }

  ////////////// COMMANDS //////////////
  // HttpRequestParts +
  type Connect = IOClient.Connect;                           val Connect = IOClient.Connect
  type Close = IOClient.Close;                               val Close = IOClient.Close
  type Send = IOClient.Send;                                 val Send = IOClient.Send
  type Tell = IOClient.Tell;                                 val Tell = IOClient.Tell
  type SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout;   val SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout
  type SetRequestTimeout = ClientFrontend.SetRequestTimeout; val SetRequestTimeout = ClientFrontend.SetRequestTimeout

  ////////////// EVENTS //////////////
  // HttpResponseParts +
  type Connected = IOClient.Connected; val Connected = IOClient.Connected
  type Closed = IOClient.Closed;       val Closed = IOClient.Closed
  type Received = IOClient.Received;   val Received = IOClient.Received
}

