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

import org.eclipse.jetty.continuation._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

/**
 * The spray connector servlet for Jetty 7.
 */
class Jetty7ConnectorServlet extends ConnectorServlet("Jetty 7") {

  override def service(req: HttpServletRequest, resp: HttpServletResponse) {
    requestContext(req, resp, responder(req, resp)).foreach(rootService ! _)
  }

  def responder(req: HttpServletRequest, resp: HttpServletResponse)(context: RequestContext): RoutingResult => Unit = {
    val continuation = ContinuationSupport.getContinuation(req)
    continuation.addContinuationListener(new ContinuationListener {
      def onTimeout(continuation: Continuation) {
        log.error("Timeout of %s", context.request)
        timeoutActor ! Timeout(context)
      }
      def onComplete(continuation: Continuation) {}
    })
    continuation.setTimeout(timeout)
    continuation.suspend(resp)
    responder { response =>
      respond(resp, response)
      continuation.complete()
    }
  }
  
}