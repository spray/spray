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

package spray.client

import akka.dispatch.{ ExecutionContext, Future }
import akka.util.duration._
import akka.actor.{ ActorRefFactory, ActorRef }
import akka.util.Timeout
import akka.pattern.ask
import akka.io.IO
import spray.httpx.{ ResponseTransformation, RequestBuilding }
import spray.can.Http
import spray.util.actorSystem
import spray.http._

object pipelining extends RequestBuilding with ResponseTransformation {

  def sendReceive(implicit refFactory: ActorRefFactory, executionContext: ExecutionContext,
                  futureTimeout: Timeout = 60.seconds): HttpRequest ⇒ Future[HttpResponse] =
    sendReceive(IO(Http)(actorSystem))

  def sendReceive(transport: ActorRef)(implicit ec: ExecutionContext,
                                       futureTimeout: Timeout): HttpRequest ⇒ Future[HttpResponse] =
    request ⇒ transport ? request map {
      case x: HttpResponse          ⇒ x
      case x: HttpResponsePart      ⇒ sys.error("sendReceive doesn't support chunked responses, try sendTo instead")
      case x: Http.ConnectionClosed ⇒ sys.error("Connection closed before reception of response: " + x)
      case x                        ⇒ sys.error("Unexpected response from HTTP transport: " + x)
    }

  def sendTo(transport: ActorRef) = new SendTo(transport)

  class SendTo(transport: ActorRef) {
    def withResponsesReceivedBy(receiver: ActorRef): HttpRequest ⇒ Unit =
      request ⇒ transport.tell(request, receiver)
  }
}
