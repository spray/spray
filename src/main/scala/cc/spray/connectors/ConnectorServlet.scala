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

import akka.util.Logging
import collection.mutable.HashMap
import collection.JavaConversions._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import utils.CantWriteResponseBodyException
import utils.ActorHelpers._

private[connectors] trait ConnectorServlet extends HttpServlet with Logging {
  
  val rootService = actor("spray-root-service")
  
  def rawRequest(req: HttpServletRequest) = new RawRequest {
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
    override def toString = "Request(" + method + ", uri=" + uri + ", headers=" + headers + ", remoteIP=" + remoteIP + ")"
  }

  def rawResponse(resp: HttpServletResponse) = new RawResponse {
    def setStatus(code: Int) { resp.setStatus(code) }
    def addHeader(name: String, value: String) { resp.addHeader(name, value) }
    def outputStream = resp.getOutputStream
  }

  def completer(resp: HttpServletResponse)(close: => Unit): (RawResponse => Unit) => Unit = { fillResponse =>
    try {
      fillResponse(rawResponse(resp))
      close
    } catch {
      case e: CantWriteResponseBodyException => {
        log.slf4j.error("Could not write response body, " +
                "probably the request has either timed out or the client has disconnected")
      }
      case e: Exception => log.slf4j.error("Could not complete request", e)
    }
  }
  
}