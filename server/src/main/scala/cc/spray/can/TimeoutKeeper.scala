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

import org.slf4j.LoggerFactory
import akka.actor.{Scheduler, Actor}
import java.util.concurrent.TimeUnit
import utils.LinkedList

// private messages
private[can] case class Timeout(context: RequestContext)
private[can] class TimeoutContext(val request: HttpRequest, val responder: HttpResponse => Unit)
        extends LinkedList.Element[TimeoutContext]
private[can] case class CancelTimeout(timeoutContext: TimeoutContext)
private[can] case object CheckForTimeouts

class TimeoutKeeper(config: CanConfig) extends Actor {
  private lazy val log = LoggerFactory.getLogger(getClass)
  private lazy val timeoutServiceActor = actor(config.timeoutServiceActorId)
  private val openRequests = new LinkedList[TimeoutContext]
  private val openTimeouts = new LinkedList[TimeoutContext]

  self.id = config.timeoutKeeperActorId

  Scheduler.schedule(self, CheckForTimeouts, config.timeoutCycle, config.timeoutCycle, TimeUnit.MILLISECONDS)

  protected def receive = {
    case ctx: TimeoutContext => openRequests += ctx

    case CancelTimeout(ctx) => ctx.memberOf -= ctx // remove from either the openRequests or the openTimeouts list

    case CheckForTimeouts => {
      openRequests.forAllTimedOut(config.requestTimeout) { ctx =>
        log.warn("A request to '{}' timed out, dispatching to the TimeoutService '{}'",
          ctx.request.uri, config.timeoutServiceActorId)
        openRequests -= ctx
        timeoutServiceActor ! Timeout(RequestContext(ctx.request, ctx.responder))
        openTimeouts += ctx
      }
      openTimeouts.forAllTimedOut(config.timeoutTimeout) { ctx =>
        log.warn("The TimeoutService for '{}' timed out as well, responding with the static error reponse", ctx.request.uri)
        ctx.responder(timeoutTimeoutResponse(ctx.request))
      }
    }
  }

  protected def timeoutTimeoutResponse(request: HttpRequest) = {
    HttpResponse(
      status = 500,
      headers = List(HttpHeader("Content-Type", "text/plain")),
      body = ("Ooops! The server was not able to produce a timely response to your request.\n" +
             "Please try again in a short while!").getBytes("ISO-8859-1")
    )
  }
}