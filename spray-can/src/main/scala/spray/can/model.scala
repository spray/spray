/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.can

import java.net.InetSocketAddress
import scala.collection.immutable
import akka.io.Inet
import akka.actor.{ ActorRefFactory, ActorRef }
import spray.can.client.HostConnectorSettings
import spray.io.ClientSSLEngineProvider
import spray.util.actorSystem
import spray.http._

sealed trait MessageLine

case class RequestLine(
  method: HttpMethod = HttpMethods.GET,
  uri: String = "/",
  protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`) extends MessageLine

case class StatusLine(
  protocol: HttpProtocol,
  status: Int,
  reason: String,
  isResponseToHeadRequest: Boolean = false) extends MessageLine

object Trailer {
  def verify(trailer: List[HttpHeader]) = {
    if (!trailer.isEmpty) {
      require(trailer forall { _.name != "Content-Length" }, "Content-Length header is not allowed in trailer")
      require(trailer forall { _.name != "Transfer-Encoding" }, "Transfer-Encoding header is not allowed in trailer")
      require(trailer forall { _.name != "Trailer" }, "Trailer header is not allowed in trailer")
    }
    trailer
  }
}

case class HostConnectorSetup(remoteAddress: InetSocketAddress,
                              options: immutable.Traversable[Inet.SocketOption],
                              settings: Option[HostConnectorSettings])(implicit sslEngineProvider: ClientSSLEngineProvider) {
  private[can] def normalized(implicit refFactory: ActorRefFactory) =
    if (settings.isDefined) this
    else copy(settings = Some(HostConnectorSettings(actorSystem)))
}
object HostConnectorSetup {
  def apply(host: String, port: Int = 80, options: immutable.Traversable[Inet.SocketOption] = Nil,
            settings: Option[HostConnectorSettings] = None)(implicit sslEngineProvider: ClientSSLEngineProvider): HostConnectorSetup =
    apply(new InetSocketAddress(host, port), options, settings)
}

case class HostConnectorInfo(hostConnector: ActorRef, setup: HostConnectorSetup)