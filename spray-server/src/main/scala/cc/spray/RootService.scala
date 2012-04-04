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
import util._
import java.util.concurrent.atomic.{AtomicInteger, AtomicBoolean}
import akka.actor.{ActorLogging, Actor, ActorRef}

/**
 * The RootService actor is the central entrypoint for HTTP requests entering the ''spray'' infrastructure.
 * It is responsible for creating an [[cc.spray.http.HttpRequest]] object for the request as well as dispatching this
 *  [[cc.spray.http.HttpRequest]] object to all attached [[cc.spray.HttpService]]s. 
 */
class RootService(firstService: ActorRef, moreServices: ActorRef*) extends Actor with ActorLogging with ErrorHandling {

  protected val handler: RequestContext => Unit = moreServices.toList match {
    case Nil => handleOneService(firstService)
    case services => handleMultipleServices(firstService :: services)
  }

  protected val initialUnmatchedPath: String => String = SprayServletSettings.RootPath match {
    case "" => identityFunc
    case rootPath => { path =>
      if (path.startsWith(rootPath)) {
        path.substring(rootPath.length)
      } else {
        log.warning("Received request outside of configured root-path, request uri '{}', configured root path '{}'", path, rootPath)
        path
      }
    }
  }

  override def preStart() {
    log.debug("Starting spray RootService ...")
    cc.spray.http.warmUp()
    super.preStart()
  }

  override def postStop() {
    log.info("spray RootService stopped")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.info("Restarting spray RootService because of previous {} ...", reason.getClass.getName)
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
    import context._
    log.debug("Received {} with one attached service, dispatching...", request)
    val newResponder = responder.withReject { rejections =>
      if (!rejections.isEmpty) log.warning("Non-empty rejection set received in RootService, ignoring ...")
      responder.complete(noServiceResponse(request))
    }
    service ! context.copy(responder = newResponder, unmatchedPath = initialUnmatchedPath(request.path))
  }

  protected def handleMultipleServices(services: List[ActorRef])(context: RequestContext) {
    import context._
    log.debug("Received {} with {} attached services, dispatching...", request, services.size)
    val responded = new AtomicBoolean(false)
    val rejected = new AtomicInteger(services.size)
    val newResponder = responder.copy(
      complete = { response =>
        if (responded.compareAndSet(false, true)) responder.complete(response)
        else log.warning("Received a second response for request '{}':\n\n{}\n\nIgnoring the additional response...", request, response)
      },
      reject = { rejections =>
        if (!rejections.isEmpty) log.warning("Non-empty rejection set received in RootService, ignoring ...")
        if (rejected.decrementAndGet() == 0) responder.complete(noServiceResponse(request))
      }
    )
    val outContext = context.copy(responder = newResponder, unmatchedPath = initialUnmatchedPath(request.path))
    services.foreach(_ ! outContext)
  }

  protected def noServiceResponse(request: HttpRequest) =
    HttpResponse(404, "No service available for [" + request.uri + "]")

  protected def timeoutResponse(request: HttpRequest) =
    HttpResponse(500, "The server could not handle the request in the appropriate time frame (async timeout)")
}

case class Timeout(context: RequestContext)