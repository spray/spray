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

import akka.actor.{Actor, ActorRef}

/**
 * The RootService actor is the central entrypoint for HTTP requests entering the ''spray'' infrastructure.
 * It is responsible for creating an [[cc.spray.http.HttpRequest]] object for the request as well as dispatching this
 *  [[cc.spray.http.HttpRequest]] object to all attached [[cc.spray.HttpService]]s. 
 */
class SprayCanRootService(firstService: ActorRef, moreServices: ActorRef*)
        extends RootService(firstService, moreServices: _*) with SprayCanSupport {

  lazy val timeoutActor = {
    val actors = Actor.registry.actorsFor(SpraySettings.TimeoutActorId)
    assert(actors.length == 1, actors.length + " actors for id '" + SpraySettings.TimeoutActorId +
            "' found, expected exactly one")
    actors.head
  }

  protected override def receive = {
    case context: can.RequestContext =>
      try handler(fromSprayCanContext(context)) catch handleExceptions(context)
    case can.Timeout(context) => {
      try {
        val ctx = fromSprayCanContext(context)
        if (self == timeoutActor) context.responder(fromSprayResponse(timeoutResponse(ctx.request)))
        else timeoutActor ! Timeout(ctx)
      } catch handleExceptions(context)
    }
  }

  protected def handleExceptions(context: can.RequestContext): PartialFunction[Throwable, Unit] = {
    case e: Exception => context.responder(fromSprayResponse(responseForException(context.request, e)))
  }
}