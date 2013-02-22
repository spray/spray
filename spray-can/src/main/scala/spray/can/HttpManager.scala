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

import akka.actor.{Terminated, ActorRef, Props, Actor}
import spray.can.client.{HttpClientSettingsGroup, ClientSettings}
import spray.can.server.HttpListener


private[can] class HttpManager(httpSettings: HttpExt#Settings) extends Actor {
  val listenerCounter = Iterator from 0
  val clientCounter = Iterator from 0

  var clients = Map.empty[Option[ClientSettings], ActorRef]

  def receive = {
    case connect: Http.Connect =>
      val client = clients.getOrElse(connect.settings, createAndRegisterClient(connect.settings))
      client forward connect

    case bind: Http.Bind =>
      val commander = sender
      context.actorOf(
        props = Props(new HttpListener(commander, bind, httpSettings)) withDispatcher httpSettings.ListenerDispatcher,
        name = "listener-" + listenerCounter.next()
      )

    case Terminated(child) =>
      clients = clients.filter(_._2 != child)
  }

  def createAndRegisterClient(settings: Option[ClientSettings]): ActorRef = {
    val client = context.actorOf(
      props = Props(new HttpClientSettingsGroup(settings getOrElse ClientSettings(context.system), httpSettings))
        .withDispatcher(httpSettings.ClientDispatcher),
      name = "group-" + clientCounter.next()
    )
    clients = clients.updated(settings, client)
    context watch client
  }

}
