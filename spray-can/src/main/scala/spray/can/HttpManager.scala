/*
 * Copyright (C) 2011-2012 spray.io
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

import akka.actor._
import spray.can.client.{HttpHostConnector, HostConnectorSettings, HttpClientSettingsGroup, ClientConnectionSettings}
import spray.can.server.HttpListener
import spray.util.SprayActorLogging
import spray.http._
import scala.util.control.NonFatal


private[can] class HttpManager(httpSettings: HttpExt#Settings) extends Actor with SprayActorLogging {
  import httpSettings._
  val listenerCounter = Iterator from 0
  val groupCounter = Iterator from 0
  val hostConnectorCounter = Iterator from 0

  var settingsGroups = Map.empty[ClientConnectionSettings, ActorRef]
  var hostConnectors = Map.empty[HostConnectorSetup, ActorRef]

  def receive = {
    case request: HttpRequest =>
      try {
        val req = request.withEffectiveUri(securedConnection = false)
        val Uri.Authority(host, port, _) = req.uri.authority
        val effectivePort = if (port == 0) Uri.defaultPorts(req.uri.scheme) else port
        val connector = hostConnectorFor(HostConnectorSetup(host.toString, effectivePort))
        connector forward req.copy(uri = Uri(path = req.uri.path)) // never render absolute URI here
      } catch {
        case NonFatal(e) =>
          log.error("Illegal request: {}", e.getMessage)
          sender ! Status.Failure(e)
      }

    case (request: HttpRequest, setup: HostConnectorSetup) =>
      hostConnectorFor(setup) forward request

    case setup: HostConnectorSetup =>
      val connector = hostConnectorFor(setup)
      sender.tell(HostConnectorInfo(connector, setup), connector)

    case connect: Http.Connect =>
      val settings = connect.settings getOrElse ClientConnectionSettings(context.system)
      settingsGroupFor(settings) forward connect

    case bind: Http.Bind =>
      val commander = sender
      context.actorOf(
        props = Props(new HttpListener(commander, bind, httpSettings)) withDispatcher ListenerDispatcher,
        name = "listener-" + listenerCounter.next()
      )

    case cmd: Http.CloseCommand =>


    case Terminated(child) =>
      settingsGroups = settingsGroups.filter(_._2 != child)
      hostConnectors = hostConnectors.filter(_._2 != child)

    case HttpHostConnector.DemandIdleShutdown =>
      hostConnectors = hostConnectors.filter(_._2 != sender)
      sender ! PoisonPill
  }

  def hostConnectorFor(setup: HostConnectorSetup): ActorRef = {
    val normalizedSetup = setup.normalized(context.system)

    def createAndRegisterHostConnector = {
      import normalizedSetup._
      val settingsGroup = settingsGroupFor(settings.get.connectionSettings) // must not be moved into the Props(...)!
      val hostConnector = context.actorOf(
        props = Props(new HttpHostConnector(normalizedSetup, settingsGroup)) withDispatcher HostConnectorDispatcher,
        name = "host-connector-" + hostConnectorCounter.next())
      hostConnectors = hostConnectors.updated(normalizedSetup, hostConnector)
      context watch hostConnector
    }
    hostConnectors.getOrElse(normalizedSetup, createAndRegisterHostConnector)
  }

  def settingsGroupFor(settings: ClientConnectionSettings): ActorRef = {
    def createAndRegisterSettingsGroup = {
      val group = context.actorOf(
        props = Props(new HttpClientSettingsGroup(settings, httpSettings)) withDispatcher SettingsGroupDispatcher,
        name = "group-" + groupCounter.next())
      settingsGroups = settingsGroups.updated(settings, group)
      context watch group
    }
    settingsGroups.getOrElse(settings, createAndRegisterSettingsGroup)
  }
}
