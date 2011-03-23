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

import akka.http.Endpoint
import http._
import akka.actor.Actor
import akka.util.Logging

trait HttpServiceActor extends HttpServiceLogic with Actor with Logging {
  
  // use the configurable dispatcher
  self.dispatcher = Endpoint.Dispatcher 

  protected def receive = {
    case request: HttpRequest => handle(request)
  }

  protected[spray] def responderForRequest(request: HttpRequest) = new (RoutingResult => Unit) {
    val channel = self.channel    
    def apply(rr:RoutingResult) = channel ! responseFromRoutingResult(rr)
  }

  override protected[spray] def responseForException(request: HttpRequest, e: Exception) = {
    log.error("Error during processing of request {}:\n{}", request, e)
    super.responseForException(request, e)
  }
}

/***
 * The default implementation of an HttpService. If you want to use a custom HttpService implementation you should
 * generate a sub trait of HttpServiceLogic (e.g. CustomServiceLogic) and create your CustomHttpService with
 * "case class CustomHttpService(route: Route) extends HttpServiceActor with CustomServiceLogic".
 * In this way you can test your CustomServiceLogic with SprayTest without the need to fire up actual actors.
 */
case class HttpService(route: Route) extends HttpServiceActor