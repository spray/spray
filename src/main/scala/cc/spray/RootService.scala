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

import akka.http._
import akka.actor.{Actor, ActorRef}
import akka.util.Logging
import http._
import akka.dispatch.{Future, Futures}
import HttpStatusCodes._

class RootService extends Actor with ServletConverter with Logging {
  private var services: List[ActorRef] = Nil
  private var handler: RequestMethod => Unit = handleNoServices  

  self.id = "spray-root-service"

  protected def receive = {
    case rm: RequestMethod => {
      try {
        handler(rm)
      } catch {
        case e: Exception => handleException(e, rm)
      }
    }
    case Attach(service) => attach(service)
    case Detach(service) => detach(service)
  }
  
  private def handleNoServices(rm: RequestMethod) {
    log.slf4j.debug("Received {} with no attached services, completing with 404", toSprayRequest(rm.request))
    rm.rawComplete(fromSprayResponse(noService(rm.request.getRequestURI)))
  }
  
  private def handleOneService(rm: RequestMethod) {
    val request = toSprayRequest(rm.request)
    log.slf4j.debug("Received {} with one attached service, dispatching...", request)
    (services.head !!! request) onComplete completeRequest(rm) _
  }
  
  private def handleMultipleServices(rm: RequestMethod) {    
    val request = toSprayRequest(rm.request)
    log.slf4j.debug("Received {} with {} attached services, dispatching...", services.size, request)
    val futures = services.map(_ !!! request).asInstanceOf[List[Future[Any]]]
    Futures.fold(None.asInstanceOf[Option[Any]])(futures) { (result, future) =>
      (result, future) match {
        case (None, None) => None
        case (None, x@ Some(_: HttpResponse)) => x
        case (x@ Some(_: HttpResponse), None) => x 
        case (x@ Some(_: HttpResponse), Some(y: HttpResponse)) => {
          log.slf4j.warn("Received a second response for request '{}':\n\nn{}\n\nIgnoring the additional response...", request, y)
          x
        }
      }
    } onComplete completeRequest(rm) _
  }
  
  private def completeRequest(rm: RequestMethod)(future: Future[Option[Any]]) {
    if (future.exception.isEmpty) {
      future.result.get match {
        case Some(response: HttpResponse) => rm.rawComplete(fromSprayResponse(response))
        case None => handleNoServices(rm)
      }  
    } else {
      handleException(future.exception.get, rm)
    }
  }

  protected def handleException(e: Throwable, rm: RequestMethod) {
    log.slf4j.error("Exception during request processing: {}", e)
    rm.rawComplete(fromSprayResponse(e match {
      case e: HttpException => HttpResponse(e.status)
      case e: Exception => HttpResponse(HttpStatus(InternalServerError, e.getMessage))
    }))
  }
  
  protected def attach(serviceActor: ActorRef) {
    services = serviceActor :: services
    if (serviceActor.isUnstarted) serviceActor.start()
    updateHandler()
  }
  
  protected def detach(serviceActor: ActorRef) {
    services = services.filter(_ != serviceActor)    
    updateHandler()
  }
  
  private def updateHandler() {
    handler = services.size match {
      case 0 => handleNoServices
      case 1 => handleOneService
      case _ => handleMultipleServices
    }
  }
  
  protected def noService(uri: String): HttpResponse = {
    HttpStatus(404, "No service available for [" + uri + "]")
  }
}

case class Attach(serviceActorRef: ActorRef)

case class Detach(serviceActorRef: ActorRef)

object Attach {
  def apply(serviceActor: => Actor): Attach = apply(Actor.actorOf(serviceActor))
}