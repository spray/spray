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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The spray connector servlet for all servlet 3.0 containers.
 */
class Servlet30ConnectorServlet extends ConnectorServlet {

  def containerName = "Servlet API 3.0"

  override def service(req: HttpServletRequest, resp: HttpServletResponse) {
    requestContext(req, resp, responder(req, resp)).foreach(rootService ! _)
  }

  def responder(req: HttpServletRequest, resp: HttpServletResponse): RequestResponder = {
    val alreadyResponded = new AtomicBoolean(false)
    val asyncContext = req.startAsync()
    asyncContext.setTimeout(timeout)
    asyncContext.addListener {
      new AsyncListener {
        def onTimeout(event: AsyncEvent) {
          if (alreadyResponded.compareAndSet(false, true)) {
            handleTimeout(req, resp) {
              asyncContext.complete()
            }
          } // else the request was completed just after the container decided to trigger a timeout
        }
        def onError(event: AsyncEvent) {
          event.getThrowable match {
            case null => log.error("Unspecified Error during async processing of {}", requestString(req))
            case ex => log.error(ex, "Error during async processing of {}", requestString(req))
          }
        }
        def onStartAsync(event: AsyncEvent) {}
        def onComplete(event: AsyncEvent) {}
      }
    }
    responderFor(req) { response =>
      if (alreadyResponded.compareAndSet(false, true)) {
        respond(req, resp, response)
        asyncContext.complete()
      } else log.warning("Received late response to {}, which already timed out, dropping response...", requestString(req))
    }
  }
}