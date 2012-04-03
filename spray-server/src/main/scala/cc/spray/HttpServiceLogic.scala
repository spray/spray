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
import HttpHeaders._
import util.IllegalResponseException

/**
 * The logic part of the [[cc.spray.HttpService]]. Contains the code for [[cc.spray.RequestContext]] creation as well
 * as translation of [[cc.spray.Rejection]]s and Exceptions to [[cc.spray.http.HttpResponse]]s. 
 */
trait HttpServiceLogic extends ErrorHandling {

  /**
   * The route of this HttpService
   */
  def route: Route

  /**
   * A RejectionHandler responsible for converting a List of Rejections into an HttpResponse.
   * If a given List of Rejections is not handled by this partial function an InternalServerError response is produced.
   */
  def rejectionHandler: RejectionHandler
  
  def handle(context: RequestContext) {
    try {
      route {
        context.withResponderTransformed { responder =>
          responder.copy(
            complete = { response => responder.complete(verifyResponse(response)) },
            reject = convertRejections(responder),
            startChunkedResponse = { response => responder.startChunkedResponse(verifyResponse(response)) }
          )
        }
      }
    } catch {
      case e: Exception => context.complete(responseForException(context.request, e))
    }
  }
  
  protected def convertRejections(responder: RequestResponder)(rejections: Set[Rejection]) {
    val activeRejections = Rejections.applyCancellations(rejections)
    if (activeRejections.isEmpty)
      responder.reject(activeRejections) // reject with empty set to RootService -> no response from this service
    else
      responder.complete(verifyResponse(fullRejectionHandler(activeRejections.toList)))
  }

  protected lazy val fullRejectionHandler: RejectionHandler = rejectionHandler orElse {
    case rejection :: _ => HttpResponse(InternalServerError, "Unknown request rejection: " + rejection)
  }
  
  protected def verifyResponse(response: HttpResponse): HttpResponse = {
    response.headers.foreach {
      case _: `Content-Type` =>
        throw new IllegalResponseException("HttpResponse must not include explicit 'Content-Type' header, " +
              "use the respective HttpContent member!")
      case _: `Content-Length` =>
        throw new IllegalResponseException("HttpResponse must not include explicit 'Content-Length' header, " +
              "this header will be set implicitly!")
      case _ =>
    }
    response
  }
  
}