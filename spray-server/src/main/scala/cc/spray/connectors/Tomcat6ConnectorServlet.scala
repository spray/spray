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

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.apache.catalina.{CometEvent, CometProcessor}

/**
 * The spray connector servlet for Tomcat 6.
 */
class Tomcat6ConnectorServlet extends ConnectorServlet("Tomcat 6") with CometProcessor {

  override def service(req: HttpServletRequest, resp: HttpServletResponse) {
    throw new RuntimeException("The Tomcat6ConnectorServlet does not support the standard blocking Servlet API, " +
            "you need to enable support for asynchronous HTTP in your Tomcat6 server instance!") 
  }

  def event(ev: CometEvent) {
    ev.getEventType match {
      case CometEvent.EventType.BEGIN => requestContext(ev).foreach(rootService ! _)
      case CometEvent.EventType.ERROR => ev.getEventSubType match {
        case CometEvent.EventSubType.TIMEOUT => requestContext(ev).foreach { ctx =>
          log.error("Timeout of %s", ctx.request)
          timeoutActor ! Timeout(ctx)
        }
        case CometEvent.EventSubType.CLIENT_DISCONNECT => log.warn("Client disconnected")
        case err => log.error("Unspecified Error during async processing: %s", err)
      }
      case CometEvent.EventType.READ => {}
      case CometEvent.EventType.END => {}
    }
  }

  def requestContext(ev: CometEvent): Option[RequestContext] = {
    val req: HttpServletRequest = ev.getHttpServletRequest
    val resp: HttpServletResponse = ev.getHttpServletResponse
    requestContext(req, resp, responder(ev))
  }

  def responder(ev: CometEvent)(context: RequestContext): RoutingResult => Unit = {
    ev.setTimeout(timeout)
    responder { response =>
      respond(ev.getHttpServletResponse, response)
      ev.close()
    }
  }

}