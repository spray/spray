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

class Jetty7ConnectorServlet extends ConnectorServlet {

  def containerName = "Jetty 7"

  override def service(req: HttpServletRequest, resp: HttpServletResponse) {
    rootService ! RawRequestContext(rawRequest(req), suspend(req, resp)) 
  }
  
  def suspend(req: HttpServletRequest, resp: HttpServletResponse): (RawResponse => Unit) => Unit = {    
    val continuation = ContinuationSupport.getContinuation(req)
    val rawReq = rawRequest(req)
    continuation.addContinuationListener(new ContinuationListener {
      def onTimeout(continuation: Continuation) {
        logError("Timeout of %s", rawReq)
        TimeOutHandler.get.apply(rawReq, rawResponse(resp))
        continuation.complete()
      }
      def onComplete(continuation: Continuation) {}
    })
    continuation.setTimeout(timeout)
    continuation.suspend(resp)    
    
    completer(resp) {
      continuation.complete()
    }
  }
  
}