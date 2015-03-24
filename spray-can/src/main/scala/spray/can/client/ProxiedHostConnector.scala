/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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

package spray.can.client

import akka.actor._
import spray.http.{ Uri, HttpHeaders, HttpRequest }

/**
 * A wrapper around a [[spray.can.client.HttpHostConnector]] that is connected to a proxy. Fixes missing Host headers and
 * relative URIs or otherwise warns if these differ from the target host/port.
 */
private[can] class ProxiedHostConnector(host: String, port: Int, proxyConnector: ActorRef) extends Actor with ActorLogging {

  import Uri._
  val authority = Authority(Host(host), port).normalizedForHttp()
  val hostHeader = HttpHeaders.Host(host, authority.port)

  context.watch(proxyConnector)

  def receive: Receive = {
    case request: HttpRequest ⇒
      val headers = request.header[HttpHeaders.Host] match {
        case Some(reqHostHeader) ⇒
          if (authority != Authority(Host(reqHostHeader.host), reqHostHeader.port).normalizedForHttp())
            log.warning(s"sending request with header '$reqHostHeader' to a proxied connection to $authority")
          request.headers
        case None ⇒
          hostHeader :: request.headers
      }
      val effectiveUri =
        if (request.uri.isRelative)
          request.uri.toEffectiveHttpRequestUri(authority.host, port)
        else {
          if (authority != request.uri.authority.normalizedForHttp())
            log.warning(s"sending request with absolute URI '${request.uri}' to a proxied connection to $authority")
          request.uri
        }
      proxyConnector.forward(request.copy(uri = effectiveUri).withHeaders(headers))

    case HttpHostConnector.DemandIdleShutdown ⇒
      proxyConnector ! PoisonPill
      context.stop(self)

    case Terminated(`proxyConnector`) ⇒
      context.stop(self)

    case x ⇒ proxyConnector.forward(x)
  }
}
