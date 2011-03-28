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
package builders

import akka.actor.Actor
import akka.util.Logging
import http._
import HttpStatusCodes._

private[spray] trait DetachedBuilders {

  def detached(route: Route)(implicit detachedActorFactory: Route => Actor): Route = { ctx =>
    Actor.actorOf(detachedActorFactory(route)).start ! ctx
  }
  
  // implicits  
  
  implicit def defaultDetachedActorFactory(route: Route): Actor = new DetachedRouteActor(route)
  
}

class DetachedRouteActor(route: Route) extends Actor with Logging {  
  protected def receive = {
    case ctx: RequestContext => {
      try {
        route(ctx)
      } catch {
        case e: Exception => ctx.complete(responseForException(ctx.request, e))
      }
    } 
  }
  
  protected def responseForException(request: HttpRequest, e: Exception): HttpResponse = {
    log.error("Error during processing of request {}:\n{}", request, e)
    e match {
      case e: HttpException => HttpResponse(e.status)
      case e: Exception => HttpResponse(HttpStatus(InternalServerError, e.getMessage)) 
    }    
  }   
} 