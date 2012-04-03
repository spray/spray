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
      case CometEvent.EventType.BEGIN => {
        requestContext(ev.getHttpServletRequest, ev.getHttpServletResponse, responder(ev)).foreach(rootService ! _)
      }
      case CometEvent.EventType.ERROR => ev.getEventSubType match {
        case CometEvent.EventSubType.TIMEOUT => {
          handleTimeout(ev.getHttpServletRequest, ev.getHttpServletResponse) {
            ev.close()
          }
        }
        case CometEvent.EventSubType.CLIENT_DISCONNECT => log.warning("Client disconnected")
        case err => log.error("Unspecified Error during async processing: {}", err)
      }
      case CometEvent.EventType.READ => {}
      case CometEvent.EventType.END => {}
    }
  }

  def responder(ev: CometEvent): RequestResponder = {
    ev.setTimeout(timeout)
    responderFor(ev.getHttpServletRequest) { response =>
      respond(ev.getHttpServletRequest, ev.getHttpServletResponse, response)
      ev.close()
    }
  }

}