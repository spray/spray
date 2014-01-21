/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import scala.util.control.NonFatal
import scala.collection.immutable
import akka.actor._
import akka.io.Inet
import spray.can.client._
import spray.can.server.HttpListener
import spray.http._
import Http.{ ClientConnectionType, HostConnectorSetup }
import HttpHostConnector.RequestContext

private[can] class HttpManager(httpSettings: HttpExt#Settings) extends Actor with ActorLogging {
  import HttpManager._
  import httpSettings._

  private[this] val listenerCounter = Iterator from 0
  private[this] val groupCounter = Iterator from 0
  private[this] val hostConnectorCounter = Iterator from 0
  private[this] val proxyConnectorCounter = Iterator from 0

  private[this] var settingsGroups = Map.empty[ClientConnectionSettings, ActorRef]
  private[this] var connectors = Map.empty[HostConnectorSetup, ActorRef]
  private[this] var listeners = Seq.empty[ActorRef]

  def receive = withTerminationManagement {
    case request: HttpRequest ⇒
      try {
        val req = request.withEffectiveUri(securedConnection = false)
        val connector = connectorForUri(req.uri)
        // never render absolute URIs here and we also drop any potentially existing fragment
        connector.forward(req.copy(uri = req.uri.toRelative.withoutFragment))
      } catch {
        case NonFatal(e) ⇒
          log.error("Illegal request: {}", e.getMessage)
          sender() ! Status.Failure(e)
      }

    // 3xx Redirect
    case ctx @ RequestContext(req, _, _, commander) ⇒
      val connector = connectorForUri(req.uri)
      // never render absolute URIs here and we also drop any potentially existing fragment
      val newReq = req.copy(uri = req.uri.toRelative.withoutFragment)
      connector.tell(ctx.copy(request = newReq), commander)

    case (request: HttpRequest, setup: HostConnectorSetup) ⇒
      connectorFor(setup).forward(request)

    case setup: HostConnectorSetup ⇒
      val connector = connectorFor(setup)
      sender().tell(Http.HostConnectorInfo(connector, setup), connector)

    case connect: Http.Connect ⇒
      settingsGroupFor(ClientConnectionSettings(connect.settings)).forward(connect)

    case bind: Http.Bind ⇒
      val commander = sender()
      listeners :+= context.watch {
        context.actorOf(
          props = Props(newHttpListener(commander, bind, httpSettings)) withDispatcher ListenerDispatcher,
          name = "listener-" + listenerCounter.next())
      }

    case cmd: Http.CloseAll ⇒ shutdownSettingsGroups(cmd, Set(sender()))
  }

  def newHttpListener(commander: ActorRef, bind: Http.Bind, httpSettings: HttpExt#Settings) =
    new HttpListener(commander, bind, httpSettings)

  def withTerminationManagement(behavior: Receive): Receive = ({
    case ev @ Terminated(child) ⇒
      if (listeners contains child)
        listeners = listeners filter (_ != child)
      else if (connectors exists (_._2 == child))
        connectors = connectors filter { _._2 != child }
      else
        settingsGroups = settingsGroups filter { _._2 != child }
      behavior.applyOrElse(ev, (_: Terminated) ⇒ ())

    case HttpHostConnector.DemandIdleShutdown ⇒
      val hostConnector = sender()
      var sendPoisonPill = true
      connectors = connectors filter {
        case (x: ProxyConnectorSetup, proxiedConnector) if x.proxyConnector == hostConnector ⇒
          proxiedConnector ! HttpHostConnector.DemandIdleShutdown
          sendPoisonPill = false // the PoisonPill will be sent by the proxiedConnector
          false
        case (_, `hostConnector`) ⇒ false
        case _                    ⇒ true
      }
      if (sendPoisonPill) hostConnector ! PoisonPill
  }: Receive) orElse behavior

  def shutdownSettingsGroups(cmd: Http.CloseAll, commanders: Set[ActorRef]): Unit =
    if (!settingsGroups.isEmpty) {
      val running: Set[ActorRef] = settingsGroups.values.map { x ⇒ x ! cmd; x }(collection.breakOut)
      context.become(closingSettingsGroups(cmd, running, commanders))
    } else shutdownConnectors(cmd, commanders)

  def closingSettingsGroups(cmd: Http.CloseAll, running: Set[ActorRef], commanders: Set[ActorRef]): Receive =
    withTerminationManagement {
      case _: Http.CloseAll ⇒ context.become(closingSettingsGroups(cmd, running, commanders + sender()))

      case Http.ClosedAll ⇒
        val stillRunning = running - sender()
        if (stillRunning.isEmpty) shutdownConnectors(cmd, commanders)
        else context.become(closingSettingsGroups(cmd, stillRunning, commanders))

      case Terminated(child) if running contains child ⇒ self.tell(Http.ClosedAll, child)
    }

  def shutdownConnectors(cmd: Http.CloseAll, commanders: Set[ActorRef]): Unit =
    if (!connectors.isEmpty) {
      val running: Set[ActorRef] = connectors.values.map { x ⇒ x ! cmd; x }(collection.breakOut)
      context.become(closingConnectors(running, commanders))
    } else shutdownListeners(commanders)

  def closingConnectors(running: Set[ActorRef], commanders: Set[ActorRef]): Receive =
    withTerminationManagement {
      case cmd: Http.CloseCommand ⇒ context.become(closingConnectors(running, commanders + sender()))

      case Http.ClosedAll ⇒
        val stillRunning = running - sender()
        if (stillRunning.isEmpty) shutdownListeners(commanders)
        else context.become(closingConnectors(stillRunning, commanders))

      case Terminated(child) if running contains child ⇒ self.tell(Http.ClosedAll, child)
    }

  def shutdownListeners(commanders: Set[ActorRef]): Unit = {
    listeners foreach { x ⇒ x ! Http.Unbind }
    context.become(unbinding(listeners.toSet, commanders))
    if (listeners.isEmpty) self ! Http.Unbound
  }

  def unbinding(running: Set[ActorRef], commanders: Set[ActorRef]): Receive =
    withTerminationManagement {
      case cmd: Http.CloseCommand ⇒ context.become(unbinding(running, commanders + sender()))

      case Http.Unbound ⇒
        val stillRunning = running - sender()
        if (stillRunning.isEmpty) {
          commanders foreach (_ ! Http.ClosedAll)
          context.become(receive)
        } else context.become(unbinding(stillRunning, commanders))

      case Terminated(child) if running contains child ⇒ self.tell(Http.Unbound, child)
    }

  def connectorForUri(uri: Uri) = {
    val host = uri.authority.host
    connectorFor(HostConnectorSetup(host.toString, uri.effectivePort, sslEncryption = uri.scheme == "https"))
  }

  def connectorFor(setup: HostConnectorSetup) = {
    val normalizedSetup = resolveAutoProxied(setup)
    import ClientConnectionType._
    normalizedSetup.connectionType match {
      case _: Proxied  ⇒ proxiedConnectorFor(normalizedSetup)
      case Direct      ⇒ hostConnectorFor(normalizedSetup)
      case AutoProxied ⇒ throw new IllegalStateException
    }
  }

  def proxiedConnectorFor(normalizedSetup: HostConnectorSetup): ActorRef = {
    val ClientConnectionType.Proxied(proxyHost, proxyPort) = normalizedSetup.connectionType
    val proxyConnector = hostConnectorFor(normalizedSetup.copy(host = proxyHost, port = proxyPort))
    val proxySetup = proxyConnectorSetup(normalizedSetup, proxyConnector)
    def createAndRegisterProxiedConnector = {
      val proxiedConnector = context.actorOf(
        props = Props(new ProxiedHostConnector(normalizedSetup.host, normalizedSetup.port, proxyConnector)),
        name = "proxy-connector-" + proxyConnectorCounter.next())
      connectors = connectors.updated(proxySetup, proxiedConnector)
      context.watch(proxiedConnector)
    }
    connectors.getOrElse(proxySetup, createAndRegisterProxiedConnector)
  }

  def hostConnectorFor(normalizedSetup: HostConnectorSetup): ActorRef = {
    def createAndRegisterHostConnector = {
      val settingsGroup = settingsGroupFor(normalizedSetup.settings.get.connectionSettings) // must not be moved into the Props(...)!
      val hostConnector = context.actorOf(
        props = Props(new HttpHostConnector(normalizedSetup, settingsGroup)) withDispatcher HostConnectorDispatcher,
        name = "host-connector-" + hostConnectorCounter.next())
      connectors = connectors.updated(normalizedSetup, hostConnector)
      context.watch(hostConnector)
    }
    connectors.getOrElse(normalizedSetup, createAndRegisterHostConnector)
  }

  def settingsGroupFor(settings: ClientConnectionSettings): ActorRef = {
    def createAndRegisterSettingsGroup = {
      val group = context.actorOf(
        props = Props(newHttpClientSettingsGroup(settings, httpSettings)) withDispatcher SettingsGroupDispatcher,
        name = "group-" + groupCounter.next())
      settingsGroups = settingsGroups.updated(settings, group)
      context.watch(group)
    }
    settingsGroups.getOrElse(settings, createAndRegisterSettingsGroup)
  }
  def newHttpClientSettingsGroup(settings: ClientConnectionSettings, httpSettings: HttpExt#Settings) =
    new HttpClientSettingsGroup(settings, httpSettings)
}

private[can] object HttpManager {
  private class ProxyConnectorSetup(host: String, port: Int, sslEncryption: Boolean,
                                    options: immutable.Traversable[Inet.SocketOption],
                                    settings: Option[HostConnectorSettings], connectionType: ClientConnectionType,
                                    defaultHeaders: List[HttpHeader], val proxyConnector: ActorRef)
      extends HostConnectorSetup(host, port, sslEncryption, options, settings, connectionType, defaultHeaders)

  private def proxyConnectorSetup(normalizedSetup: HostConnectorSetup, proxyConnector: ActorRef) = {
    import normalizedSetup._
    new ProxyConnectorSetup(host, port, sslEncryption, options, settings, connectionType, defaultHeaders, proxyConnector)
  }

  def resolveAutoProxied(setup: HostConnectorSetup)(implicit refFactory: ActorRefFactory) = {
    val normalizedSetup = setup.normalized
    import normalizedSetup._
    val resolved =
      if (sslEncryption) ClientConnectionType.Direct // TODO
      else connectionType match {
        case ClientConnectionType.AutoProxied ⇒
          val scheme = Uri.httpScheme(sslEncryption)
          val proxySettings = settings.get.connectionSettings.proxySettings.get(scheme)
          proxySettings.filter(_.matchesHost(host)) match {
            case Some(ProxySettings(proxyHost, proxyPort, _)) ⇒ ClientConnectionType.Proxied(proxyHost, proxyPort)
            case None                                         ⇒ ClientConnectionType.Direct
          }
        case x ⇒ x
      }
    normalizedSetup.copy(connectionType = resolved)
  }
}
