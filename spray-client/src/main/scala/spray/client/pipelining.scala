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

package spray.client

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask
import spray.httpx.{ResponseTransformation, RequestBuilding}
import spray.can.client.HttpClientConnection
import spray.http._


object pipelining extends RequestBuilding with ResponseTransformation {

  def sendReceive(transport: ActorRef, futureTimeout: Timeout = 30 seconds span)
                 (implicit ec: ExecutionContext): HttpRequest => Future[HttpResponse] =
    request => transport.ask(request)(futureTimeout).map {
      case x: HttpResponse => x
      case x: HttpResponsePart => sys.error("spray.client.pipelining.sendReceive doesn't support chunked responses, " +
        "try sendTo instead")
      case HttpClientConnection.Closed(connection, reason) => sys.error("Connection closed before reception of " +
        "response, reason: " + reason)
      case x => sys.error("Unexpected response from connection: " + x)
    }

  def sendTo(transport: ActorRef) = new SendTo(transport)

  class SendTo(transport: ActorRef) {
    def withResponsesReceivedBy(receiver: ActorRef): HttpRequest => Unit =
      request => transport.tell(request, receiver)
  }
}