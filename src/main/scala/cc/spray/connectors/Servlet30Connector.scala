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
package connectors

import javax.servlet.{AsyncEvent, AsyncListener}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import collection.JavaConversions._
import collection.mutable.HashMap
import utils.ActorHelpers._
import akka.util.Logging

class Servlet30Connector extends HttpServlet with AsyncListener with Logging {
  
  val rootService = actor("spray-root-service")
  
  override def init() {
    log.info("Initializing the Servlet 3.0 <=> Spray Connector")
  }

  override def service(req: HttpServletRequest, resp: HttpServletResponse) {
    rootService ! RawRequestContext(createRawRequest(req), suspend(req, resp)) 
  }
  
  def createRawRequest(req: HttpServletRequest) = new RawRequest {
    def method = req.getMethod
    lazy val uri = {
      val buffer = req.getRequestURL
      val queryString = req.getQueryString
      if (queryString != null && queryString.length > 1) buffer.append('?').append(queryString)
      buffer.toString
    }
    lazy val headers = {
      val map = HashMap.empty[String, String]
      for (name <- req.getHeaderNames.toList; value <- req.getHeaders(name).toList) {
        map.update(name, value)
      }
      map
    }
    def inputStream = req.getInputStream
    def remoteIP = req.getRemoteAddr
    def protocol = req.getProtocol
  }

  def createRawResponse(resp: HttpServletResponse) = new RawResponse {
    def setStatus(code: Int) { resp.setStatus(code) }
    def addHeader(name: String, value: String) { resp.addHeader(name, value) }
    def outputStream = resp.getOutputStream
  }
  
  def suspend(req: HttpServletRequest, resp: HttpServletResponse): (RawResponse => Unit) => Unit = {
    val asyncContext = req.startAsync()
    asyncContext.setTimeout(Settings.AsyncTimeout)
    asyncContext.addListener(this)
    
    { completer =>
      completer(createRawResponse(resp))
      try {
        asyncContext.complete()
      } catch {
        case e: Exception => log.slf4j.error("Could not complete request: {}", e.toString)
      }
    }
  }
  
  //********************** AsyncListener **********************

  def onError(ev: AsyncEvent) {
    ev.getThrowable match {
      case null => log.warn("Unspecified Error during async request processing")
      case   ex => log.warn("Error during async request processing: {}", ex)
    }
  }

  def onTimeout(ev: AsyncEvent) {
    log.slf4j.warn("Time out of request: {}", ev.getSuppliedRequest)
    TimeOutHandler.get.apply(
      createRawRequest(ev.getSuppliedRequest.asInstanceOf[HttpServletRequest]),
      createRawResponse(ev.getSuppliedResponse.asInstanceOf[HttpServletResponse])
    )
    ev.getAsyncContext.complete()
  }

  def onComplete(ev: AsyncEvent) {}

  def onStartAsync(ev: AsyncEvent) {}
}