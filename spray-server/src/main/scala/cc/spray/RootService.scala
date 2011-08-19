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
import StatusCodes._
import akka.actor.{Actor, ActorRef}
import akka.dispatch.{Future, Futures}
import utils.{PostStart, Logging}
import akka.util.Duration

/**
 * The RootService actor is the central entrypoint for HTTP requests entering the ''spray'' infrastructure.
 * It is responsible for creating an [[cc.spray.http.HttpRequest]] object for the request as well as dispatching this
 *  [[cc.spray.http.HttpRequest]] object to all attached [[cc.spray.HttpService]]s. 
 */
class RootService(firstService: ActorRef, moreServices: ActorRef*) extends Actor with ToFromRawConverter with Logging with PostStart {

  private val handler: RawRequestContext => Unit = moreServices.toList match {
    case Nil => handleOneService(firstService)
    case services => handleMultipleServices(firstService :: services)
  }

  self.id = SpraySettings.RootActorId

  lazy val addConnectionCloseResponseHeader = SpraySettings.CloseConnection

  implicit val timeout = Actor.Timeout(Duration(SpraySettings.AsyncTimeout, "millis"))

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
    case rawContext: RawRequestContext => {
      try {
        handler(rawContext)
      } catch {
        case e: Exception => handleException(e, rawContext)
      }
    }
  }
  
  private def handleOneService(service: ActorRef)(rawContext: RawRequestContext) {
    val request = toSprayRequest(rawContext.request)
    log.debug("Received %s with one attached service, dispatching...", request)
    (service ? request).onComplete(completeRequest(rawContext) _)
  }

  private def handleMultipleServices(services: List[ActorRef])(rawContext: RawRequestContext) {
    val request = toSprayRequest(rawContext.request)
    log.debug("Received %s with %s attached services, dispatching...", request, services.size)
    val serviceFutures = services.map(_ ? request)
    val resultsFuture = Futures.fold(None.asInstanceOf[Option[HttpResponse]], SpraySettings.AsyncTimeout)(serviceFutures) {
      case (None, None) => None
      case (None, Some(x: HttpResponse)) => Some(x)
      case (x: Some[_], None) => x
      case (x, Some(y: HttpResponse)) =>
        log.warn("Received a second response for request '%s':\n\n%s\n\nIgnoring the additional response...", request, y)
        x
      case (x, y) =>
        log.warn("Received illegal response for request '%s':\n\n%s\n\nIgnoring the response...", request, y)
        x
    }
    resultsFuture.onComplete(completeRequest(rawContext) _)
  }

  private def completeNoService(rawContext: RawRequestContext) {
    rawContext.complete(fromSprayResponse(noService(rawContext.request.uri)))
  }

  private def completeRequest(rawContext: RawRequestContext)(future: Future[Any]) {
    if (future.exception.isEmpty) {
      future.result.get match {
        case Some(response: HttpResponse) => rawContext.complete(fromSprayResponse(response))
        case None => completeNoService(rawContext)
        case x =>
          log.warn("Received illegal response for request to '%s':\n\n%s\n\nCompleting with 'no service' response...",
            rawContext.request.uri, x)
            completeNoService(rawContext)
      }
    } else {
      handleException(future.exception.get, rawContext)
    }
  }

  protected def handleException(e: Throwable, rawContext: RawRequestContext) {
    log.error(e, "Exception during request processing")
    rawContext.complete(fromSprayResponse(e match {
      case e: HttpException => HttpResponse(e.failure)
      case e: Exception => HttpResponse(InternalServerError, e.getMessage)
    }))
  }

  protected def noService(uri: String) = HttpResponse(404, "No service available for [" + uri + "]")
}

object RootService {
  def apply(firstService: ActorRef, moreServices: ActorRef*): RootService =
    new RootService(firstService, moreServices: _*)
}