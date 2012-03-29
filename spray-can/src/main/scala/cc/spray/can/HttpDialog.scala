/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

import cc.spray.util.Reply
import model.{HttpResponsePart, HttpRequest, HttpResponse}
import akka.dispatch.{Promise, Future}
import collection.mutable.ListBuffer
import akka.actor._


object HttpDialog {
  private sealed abstract class Action
  private case class Connect(host: String, port: Int) extends Action
  private case class Send(request: HttpRequest) extends Action

  private class DialogActor(result: Promise[AnyRef], client: ActorRef) extends Actor with ActorLogging {
    val responses = ListBuffer.empty[HttpResponsePart]
    var connection: ActorRef = _
    var responsesPending = 0

    def receive = {
      case Connect(host, port) :: tail =>
        client.tell(HttpClient.Connect(host, port), Reply.withContext(tail))

      case Send(request) :: tail =>
        connection.tell(request, Reply.withContext(tail))
        responsesPending += 1

      case Reply(HttpClient.Connected(handle), actions) =>
        connection = handle.handler
        self ! actions

      case Reply(x: HttpResponsePart, actions: List[_]) =>
        responses += x
        responsesPending -= 1
        if (actions.isEmpty) {
          if (responsesPending == 0) {
            responses.toList match {
              case (singleResponse: HttpResponse) :: Nil => result.success(singleResponse)
              case severalResponses => result.success(severalResponses)
            }
            context.stop(self)
          }
        } else self ! actions

      case x => log.error("Not handled {}", x)
    }
  }

  private[HttpDialog] class Context(val system: ActorSystem, val client: ActorRef, val actions: List[Action])

  sealed trait GoOne { this: Context =>
    def go(): Future[HttpResponse] = {
      val result = Promise[AnyRef]()(system.dispatcher)
      system.actorOf(Props(new DialogActor(result, client))) ! actions.reverse
      result.mapTo[HttpResponse]
    }
  }

  sealed trait SendOne { this: Context =>
    def send(request: HttpRequest): GoOne =
      new Context(system, client, Send(request) :: actions) with GoOne
  }

  /**
   * Constructs a new `HttpDialog` for a connection to the given host and port.
   */
  def apply(httpClient: ActorRef, host: String, port: Int = 80)(implicit system: ActorSystem): SendOne =
    new Context(system, httpClient, List(new Connect(host, port))) with SendOne


}