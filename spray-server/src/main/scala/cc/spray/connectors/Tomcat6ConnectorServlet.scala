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
class Tomcat6ConnectorServlet extends ConnectorServlet with CometProcessor {
  
  def containerName = "Tomcat 6"

  override def service(req: HttpServletRequest, resp: HttpServletResponse) {
    throw new RuntimeException("The Tomcat6ConnectorServlet does not support the standard blocking Servlet API, " +
            "you need to enable support for asynchronous HTTP in your Tomcat6 server instance!") 
  }

  def event(ev: CometEvent) {
    val req = rawRequest(ev.getHttpServletRequest)
    ev.getEventType match {
      case CometEvent.EventType.BEGIN => {
        rootService ! RawRequestContext(req, completer(ev))
      }
      case CometEvent.EventType.ERROR => ev.getEventSubType match {
        case CometEvent.EventSubType.TIMEOUT => timeout(ev, req)
        case CometEvent.EventSubType.CLIENT_DISCONNECT => log.warn("Client disconnected for %s", req)
        case err => log.error("Unspecified Error during async processing of %s:\n%s", req, err)
      }
      case CometEvent.EventType.READ => {}
      case CometEvent.EventType.END => {}
    }
  }

  def completer(ev: CometEvent): (RawResponse => Unit) => Unit = {
    ev.setTimeout(timeout)
    completer(ev.getHttpServletResponse) {
      ev.close()
    }
  }
  
  def timeout(ev: CometEvent, req: RawRequest) {
    val resp = rawResponse(ev.getHttpServletResponse)
    log.error("Timeout of %s", req)
    TimeOutHandler.get.apply(req, resp)
    ev.close()
  }
  
}