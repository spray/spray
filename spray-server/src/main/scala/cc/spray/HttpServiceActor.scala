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

import http._
import akka.actor.Actor
import utils.{PostStart, Logging}

/**
 * The actor part of the [[cc.spray.HttpService]].
 */
trait HttpServiceActor extends Actor with ErrorHandling with Logging with PostStart {
  this: HttpServiceLogic =>
  
  override def preStart() {
    log.debug("Starting ...")
    super.preStart()
  }

  def postStart() {
    log.info("HTTP Service started")
  }

  override def postStop() {
    log.info("Stopped")
  }

  override def preRestart(reason: Throwable) {
    log.info("Restarting because of previous %s", reason.getClass.getName)
  }

  protected def receive = {
    case request: HttpRequest => handle(request)
  }

  protected[spray] def responderForRequest(request: HttpRequest): RoutingResult => Unit = {
    val channel = self.channel;
    { rr => channel ! responseFromRoutingResult(rr) }
  }
  
}

/**
 * The default implementation of an HttpService. It combines the [[cc.spray.HttpServiceActor]] with
 * the [[cc.spray.HttpServiceLogic]]. If you'd like to use a custom [[cc.spray.HttpServiceLogic]] you should generate
 * a sub trait of [[cc.spray.HttpServiceLogic]] (e.g. CustomServiceLogic) and create your CustomHttpService with
 * 
 * {{{ case class CustomHttpService(
 *       route: Route,
 *       val customRejectionHandler: PartialFunction[List[Rejection], HttpResponse]
 *     ) extends HttpServiceActor with CustomServiceLogic }}}
 * 
 * In this way you can test your CustomServiceLogic with [[cc.spray.test.SprayTest]] without the need to fire up
 * actual actors.
 */
class HttpService(val route: Route,
                  val customRejectionHandler: PartialFunction[List[Rejection], HttpResponse])
        extends HttpServiceActor with HttpServiceLogic

object HttpService {
  def apply(route: Route, customRejectionHandler: PartialFunction[List[Rejection], HttpResponse] = emptyPartialFunc) =
    new HttpService(route, customRejectionHandler)
}