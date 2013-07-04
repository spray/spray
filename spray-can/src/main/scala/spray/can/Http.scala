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
import com.typesafe.config.Config
import scala.collection.immutable
import akka.io.{ Inet, Tcp }
import akka.actor._
import spray.can.server.ServerSettings
import spray.can.client.{ HostConnectorSettings, ClientConnectionSettings }
import spray.io.{ ConnectionTimeouts, ClientSSLEngineProvider, ServerSSLEngineProvider }
import spray.http.{ HttpResponse, HttpRequest, HttpMessagePart, HttpMessagePartWrapper }
import spray.util.actorSystem

object Http extends ExtensionKey[HttpExt] {

  /// COMMANDS
  type Command = Tcp.Command

  case class Connect(remoteAddress: InetSocketAddress,
                     localAddress: Option[InetSocketAddress],
                     options: immutable.Traversable[Inet.SocketOption],
                     settings: Option[ClientConnectionSettings])(implicit val sslEngineProvider: ClientSSLEngineProvider) extends Command
  object Connect {
    def apply(host: String, port: Int = 80, localAddress: Option[InetSocketAddress] = None,
              options: immutable.Traversable[Inet.SocketOption] = Nil, settings: Option[ClientConnectionSettings] = None)(implicit sslEngineProvider: ClientSSLEngineProvider): Connect =
      apply(new InetSocketAddress(host, port), localAddress, options, settings)
  }

  case class Bind(listener: ActorRef,
                  endpoint: InetSocketAddress,
                  backlog: Int,
                  options: immutable.Traversable[Inet.SocketOption],
                  settings: Option[ServerSettings])(implicit val sslEngineProvider: ServerSSLEngineProvider) extends Command
  object Bind {
    def apply(listener: ActorRef, interface: String, port: Int = 80, backlog: Int = 100,
              options: immutable.Traversable[Inet.SocketOption] = Nil, settings: Option[ServerSettings] = None)(implicit sslEngineProvider: ServerSSLEngineProvider): Bind =
      apply(listener, new InetSocketAddress(interface, port), backlog, options, settings)
  }

  case class HostConnectorSetup(remoteAddress: InetSocketAddress,
                                options: immutable.Traversable[Inet.SocketOption],
                                settings: Option[HostConnectorSettings])(implicit val sslEngineProvider: ClientSSLEngineProvider) extends Command {
    private[can] def normalized(implicit refFactory: ActorRefFactory) =
      if (settings.isDefined) this
      else copy(settings = Some(HostConnectorSettings(actorSystem)))
  }
  object HostConnectorSetup {
    def apply(host: String, port: Int = 80, options: immutable.Traversable[Inet.SocketOption] = Nil,
              settings: Option[HostConnectorSettings] = None)(implicit sslEngineProvider: ClientSSLEngineProvider): HostConnectorSetup =
      apply(new InetSocketAddress(host, port), options, settings)

    def apply(host: String, port: Int, sslEncryption: Boolean)(implicit refFactory: ActorRefFactory, sslEngineProvider: ClientSSLEngineProvider): HostConnectorSetup = {
      val connectionSettings = ClientConnectionSettings(actorSystem).copy(sslEncryption = sslEncryption)
      apply(host, port, settings = Some(HostConnectorSettings(actorSystem).copy(connectionSettings = connectionSettings)))
    }
  }

  type FastPath = PartialFunction[HttpRequest, HttpResponse]

  // we don't use PartialFunction.empty for the default fastPath because we need serializability
  case object EmptyFastPath extends FastPath {
    def isDefinedAt(x: HttpRequest) = false
    def apply(x: HttpRequest) = throw new MatchError(x)
  }

  case class Register(handler: ActorRef,
                      keepOpenOnPeerClosed: Boolean = false,
                      fastPath: FastPath = EmptyFastPath) extends Command

  val Unbind = Tcp.Unbind

  type CloseCommand = Tcp.CloseCommand
  val Close = Tcp.Close
  val ConfirmedClose = Tcp.ConfirmedClose
  val Abort = Tcp.Abort

  case class CloseAll(kind: CloseCommand) extends Command
  object CloseAll extends CloseAll(Close)

  case object ClearStats extends Command
  case object GetStats extends Command

  type SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout; val SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout

  case class MessageCommand(cmd: HttpMessagePartWrapper) extends Command

  /// EVENTS
  type Event = Tcp.Event

  type Connected = Tcp.Connected; val Connected = Tcp.Connected
  type Bound = Tcp.Bound; val Bound = Tcp.Bound
  val Unbound = Tcp.Unbound
  type ConnectionClosed = Tcp.ConnectionClosed

  val Closed = Tcp.Closed
  val Aborted = Tcp.Aborted
  val ConfirmedClosed = Tcp.ConfirmedClosed
  val PeerClosed = Tcp.PeerClosed
  type ErrorClosed = Tcp.ErrorClosed; val ErrorClosed = Tcp.ErrorClosed

  case object ClosedAll extends Event

  type CommandFailed = Tcp.CommandFailed; val CommandFailed = Tcp.CommandFailed
  case class SendFailed(part: HttpMessagePart) extends Event
  case class MessageEvent(ev: HttpMessagePart) extends Event

  case class HostConnectorInfo(hostConnector: ActorRef, setup: HostConnectorSetup) extends Event
}

class HttpExt(system: ExtendedActorSystem) extends akka.io.IO.Extension {

  val Settings = new Settings(system.settings.config getConfig "spray.can")
  class Settings private[HttpExt] (config: Config) {
    val ManagerDispatcher = config getString "manager-dispatcher"
    val SettingsGroupDispatcher = config getString "settings-group-dispatcher"
    val HostConnectorDispatcher = config getString "host-connector-dispatcher"
    val ListenerDispatcher = config getString "listener-dispatcher"
    val ConnectionDispatcher = config getString "connection-dispatcher"
  }

  val manager = system.actorOf(
    props = Props(new HttpManager(Settings)) withDispatcher Settings.ManagerDispatcher,
    name = "IO-HTTP")
}
