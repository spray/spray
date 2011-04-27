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
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

class Servlet30ConnectorServlet extends ConnectorServlet with AsyncListener {
  
  override def init() {
    log.slf4j.info("Initializing Servlet 3.0 <=> Spray Connector")
  }

  override def service(req: HttpServletRequest, resp: HttpServletResponse) {
    rootService ! RawRequestContext(rawRequest(req), suspend(req, resp)) 
  }
  
  def suspend(req: HttpServletRequest, resp: HttpServletResponse): (RawResponse => Unit) => Unit = {
    val asyncContext = req.startAsync()
    asyncContext.setTimeout(Settings.AsyncTimeout)
    asyncContext.addListener(this)
    completer(resp) {
      asyncContext.complete()
    }
  }
  
  //********************** AsyncListener **********************

  def onError(ev: AsyncEvent) {
    val req = rawRequest(ev.getSuppliedRequest.asInstanceOf[HttpServletRequest])
    ev.getThrowable match {
      case null => log.slf4j.warn("Unspecified Error during async processing of {}", req)
      case ex => log.slf4j.warn("Error during async processing of " + req, ex)
    }
  }

  def onTimeout(ev: AsyncEvent) {
    val req = rawRequest(ev.getSuppliedRequest.asInstanceOf[HttpServletRequest])
    log.slf4j.warn("Timeout of {}", req)
    TimeOutHandler.get.apply(req, rawResponse(ev.getSuppliedResponse.asInstanceOf[HttpServletResponse]))
    ev.getAsyncContext.complete()
  }

  def onComplete(ev: AsyncEvent) {}

  def onStartAsync(ev: AsyncEvent) {}
}