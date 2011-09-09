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
import akka.actor.{Actor, ActorRef}
import utils.{PostStart, Logging}
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The RootService actor is the central entrypoint for HTTP requests entering the ''spray'' infrastructure.
 * It is responsible for creating an [[cc.spray.http.HttpRequest]] object for the request as well as dispatching this
 *  [[cc.spray.http.HttpRequest]] object to all attached [[cc.spray.HttpService]]s. 
 */
class RootService(firstService: ActorRef, moreServices: ActorRef*) extends Actor
  with Logging with ErrorHandling with PostStart {

  protected val handler: RequestContext => Unit = moreServices.toList match {
    case Nil => handleOneService(firstService)
    case services => handleMultipleServices(firstService :: services)
  }

  protected val initialUnmatchedPath: String => String = SpraySettings.RootPath match {
    case Some(rootPath) => { path =>
      if (path.startsWith(rootPath)) {
        path.substring(rootPath.length)
      } else {
        log.warn("Received request outside of configured root-path, request uri '%s', configured root path '%s'", path, rootPath)
        path
      }
    }
    case None => identity
  }

  self.id = SpraySettings.RootActorId

  override def preStart() {
    log.debug("Starting spray RootService ...")
    super.preStart()
  }

  def postStart() {
    cc.spray.http.warmUp()
    log.info("spray RootService started")
  }

  override def postStop() {
    log.info("spray RootService stopped")
  }

  override def preRestart(reason: Throwable) {
    log.info("Restarting spray RootService because of previous %s ...", reason.getClass.getName)
  }

  override def postRestart(reason: Throwable) {
    log.info("spray RootService restarted");
  }

  protected def receive = {
    case context: RequestContext =>
      try handler(context) catch handleExceptions(context)
    case Timeout(context) =>
      try context.complete(timeoutResponse(context.request)) catch handleExceptions(context)
  }

  protected def handleExceptions(context: RequestContext): PartialFunction[Throwable, Unit] = {
    case e: Exception => context.complete(responseForException(context.request, e))
  }

  protected def handleOneService(service: ActorRef)(context: RequestContext) {
    log.debug("Received %s with one attached service, dispatching...", context.request)
    val newResponder: RoutingResult => Unit = {
      case x: Respond => context.responder(x)
      case x: Reject => context.responder(Respond(noServiceResponse(context.request)))
    }
    service ! context.copy(responder = newResponder, unmatchedPath = initialUnmatchedPath(context.request.path))
  }

  protected def handleMultipleServices(services: List[ActorRef])(context: RequestContext) {
    log.debug("Received %s with %s attached services, dispatching...", context.request, services.size)
    val responded = new AtomicBoolean(false)
    val newResponder: RoutingResult => Unit = {
      case x: Respond => {
        if (responded.compareAndSet(false, true)) {
          context.responder(x)
        } else  log.warn("Received a second response for request '%s':\n\n%s\n\nIgnoring the additional response...",
          context.request, x)
      }
      case x: Reject => // ignore
    }
    val outContext = context.copy(responder = newResponder, unmatchedPath = initialUnmatchedPath(context.request.path))
    services.foreach(_ ! outContext)
  }

  protected def noServiceResponse(request: HttpRequest) =
    HttpResponse(404, "No service available for [" + request.uri + "]")

  protected def timeoutResponse(request: HttpRequest) =
    HttpResponse(500, "The server could not handle the request in the appropriate time frame (async timeout)")
}

object RootService {
  def apply(firstService: ActorRef, moreServices: ActorRef*): RootService =
    new RootService(firstService, moreServices: _*)
}

case class Timeout(context: RequestContext)