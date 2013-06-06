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

import akka.util.NonFatal
import akka.actor._
import spray.can.client.{ HttpHostConnector, HttpClientSettingsGroup, ClientConnectionSettings }
import spray.can.server.HttpListener
import spray.util.SprayActorLogging
import spray.http._

private[can] class HttpManager(httpSettings: HttpExt#Settings) extends Actor with SprayActorLogging {
  import httpSettings._
  private[this] val listenerCounter = Iterator from 0
  private[this] val groupCounter = Iterator from 0
  private[this] val hostConnectorCounter = Iterator from 0

  private[this] var settingsGroups = Map.empty[ClientConnectionSettings, ActorRef]
  private[this] var hostConnectors = Map.empty[HostConnectorSetup, ActorRef]
  private[this] var listeners = Seq.empty[ActorRef]

  def receive = withTerminationManagement {
    case request: HttpRequest ⇒
      try {
        val req = request.withEffectiveUri(securedConnection = false)
        val Uri.Authority(host, port, _) = req.uri.authority
        val effectivePort = if (port == 0) Uri.defaultPorts(req.uri.scheme) else port
        val connector = hostConnectorFor(HostConnectorSetup(host.toString, effectivePort, req.uri.scheme == "https"))
        // never render absolute URI here
        connector.forward(req.copy(uri = req.uri.copy(scheme = "", authority = Uri.Authority.Empty)))
      } catch {
        case NonFatal(e) ⇒
          log.error("Illegal request: {}", e.getMessage)
          sender ! Status.Failure(e)
      }

    case (request: HttpRequest, setup: HostConnectorSetup) ⇒
      hostConnectorFor(setup).forward(request)

    case setup: HostConnectorSetup ⇒
      val connector = hostConnectorFor(setup)
      sender.tell(HostConnectorInfo(connector, setup), connector)

    case connect: Http.Connect ⇒
      settingsGroupFor(ClientConnectionSettings(connect.settings)).forward(connect)

    case bind: Http.Bind ⇒
      val commander = sender
      listeners :+= context.watch {
        context.actorOf(
          props = Props(new HttpListener(commander, bind, httpSettings)) withDispatcher ListenerDispatcher,
          name = "listener-" + listenerCounter.next())
      }

    case cmd: Http.CloseAll ⇒ shutdownSettingsGroups(cmd, Set(sender))
  }

  def withTerminationManagement(behavior: Receive): Receive = ({
    case ev @ Terminated(child) ⇒
      if (listeners contains child)
        listeners = listeners filter (_ != child)
      else if (hostConnectors exists (_._2 == child))
        hostConnectors = hostConnectors filter { _._2 != child }
      else
        settingsGroups = settingsGroups filter { _._2 != child }
      behavior.lift(ev).getOrElse((_: Terminated) ⇒ ())

    case HttpHostConnector.DemandIdleShutdown ⇒
      hostConnectors = hostConnectors filter { _._2 != sender }
      sender ! PoisonPill
  }: Receive) orElse behavior

  def shutdownSettingsGroups(cmd: Http.CloseAll, commanders: Set[ActorRef]): Unit =
    if (!settingsGroups.isEmpty) {
      val running: Set[ActorRef] = settingsGroups.map { x ⇒ x._2 ! cmd; x._2 }(collection.breakOut)
      context.become(closingSettingsGroups(cmd, running, commanders))
    } else shutdownHostConnectors(cmd, commanders)

  def closingSettingsGroups(cmd: Http.CloseAll, running: Set[ActorRef], commanders: Set[ActorRef]): Receive =
    withTerminationManagement {
      case _: Http.CloseAll ⇒ context.become(closingSettingsGroups(cmd, running, commanders + sender))

      case Http.ClosedAll ⇒
        val stillRunning = running - sender
        if (stillRunning.isEmpty) shutdownHostConnectors(cmd, commanders)
        else context.become(closingSettingsGroups(cmd, stillRunning, commanders))

      case Terminated(child) if running contains child ⇒ self.tell(Http.ClosedAll, child)
    }

  def shutdownHostConnectors(cmd: Http.CloseAll, commanders: Set[ActorRef]): Unit =
    if (!hostConnectors.isEmpty) {
      val running: Set[ActorRef] = hostConnectors.map { x ⇒ x._2 ! cmd; x._2 }(collection.breakOut)
      context.become(closingHostConnectors(running, commanders))
    } else shutdownListeners(commanders)

  def closingHostConnectors(running: Set[ActorRef], commanders: Set[ActorRef]): Receive =
    withTerminationManagement {
      case cmd: Http.CloseCommand ⇒ context.become(closingHostConnectors(running, commanders + sender))

      case Http.ClosedAll ⇒
        val stillRunning = running - sender
        if (stillRunning.isEmpty) shutdownListeners(commanders)
        else context.become(closingHostConnectors(stillRunning, commanders))

      case Terminated(child) if running contains child ⇒ self.tell(Http.ClosedAll, child)
    }

  def shutdownListeners(commanders: Set[ActorRef]): Unit = {
    listeners foreach { x ⇒ x ! Http.Unbind }
    context.become(unbinding(listeners.toSet, commanders))
    if (listeners.isEmpty) self ! Http.Unbound
  }

  def unbinding(running: Set[ActorRef], commanders: Set[ActorRef]): Receive =
    withTerminationManagement {
      case cmd: Http.CloseCommand ⇒ context.become(unbinding(running, commanders + sender))

      case Http.Unbound ⇒
        val stillRunning = running - sender
        if (stillRunning.isEmpty) {
          commanders foreach (_ ! Http.ClosedAll)
          context.become(receive)
        } else context.become(unbinding(stillRunning, commanders))

      case Terminated(child) if running contains child ⇒ self.tell(Http.Unbound, child)
    }

  def hostConnectorFor(setup: HostConnectorSetup): ActorRef = {
    val normalizedSetup = setup.normalized

    def createAndRegisterHostConnector = {
      import normalizedSetup._
      val settingsGroup = settingsGroupFor(settings.get.connectionSettings) // must not be moved into the Props(...)!
      val hostConnector = context.actorOf(
        props = Props(new HttpHostConnector(normalizedSetup, settingsGroup)) withDispatcher HostConnectorDispatcher,
        name = "host-connector-" + hostConnectorCounter.next())
      hostConnectors = hostConnectors.updated(normalizedSetup, hostConnector)
      context.watch(hostConnector)
    }
    hostConnectors.getOrElse(normalizedSetup, createAndRegisterHostConnector)
  }

  def settingsGroupFor(settings: ClientConnectionSettings): ActorRef = {
    def createAndRegisterSettingsGroup = {
      val group = context.actorOf(
        props = Props(new HttpClientSettingsGroup(settings, httpSettings)) withDispatcher SettingsGroupDispatcher,
        name = "group-" + groupCounter.next())
      settingsGroups = settingsGroups.updated(settings, group)
      context.watch(group)
    }
    settingsGroups.getOrElse(settings, createAndRegisterSettingsGroup)
  }
}
