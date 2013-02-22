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

package spray.can.server

import scala.concurrent.duration.Duration
import akka.actor.{ReceiveTimeout, Props, ActorRef, Actor}
import akka.io.{IO, Tcp}
import spray.can.server.StatsSupport.StatsHolder
import spray.util.SprayActorLogging
import spray.can.{HttpExt, Http}


private[can] class HttpListener(bindCommander: ActorRef,
                                bind: Http.Bind,
                                httpSettings: HttpExt#Settings) extends Actor with SprayActorLogging {
  import context.system
  import bind._

  val connectionCounter = Iterator from 0
  val settings = bind.settings getOrElse ServerSettings(system)
  val statsHolder = if (settings.statsSupport) Some(new StatsHolder) else None
  val pipelineStage = HttpIncomingConnection.pipelineStage(settings, statsHolder)

  context watch handler // sign death pact

  log.debug("Binding to {}", endpoint)

  IO(Tcp) ! Tcp.Bind(self, endpoint,backlog, options)

  if (settings.bindTimeout ne Duration.Undefined)
    context setReceiveTimeout settings.bindTimeout

  def receive = {
    case Tcp.Bound =>
      context setReceiveTimeout Duration.Undefined
      log.info("Bound to {}", endpoint)
      bindCommander ! Http.Bound
      context become connected(sender)

    case Tcp.CommandFailed(_: Tcp.Bind) =>
      bindCommander ! Http.CommandFailed(bind)
      context stop self

    case ReceiveTimeout ⇒
      log.warning("Configured binding timeout of {} expired, stopping", settings.bindTimeout)
      context stop self
  }

  def connected(tcpListener: ActorRef): Receive = {
    case Tcp.Connected(remoteAddress, localAddress) =>
      val conn = sender
      context.actorOf(
        props = Props(new HttpIncomingConnection(conn, handler, pipelineStage, remoteAddress, localAddress, settings))
          .withDispatcher(httpSettings.ConnectionDispatcher),
        name = connectionCounter.next().toString
      )

    case Http.GetStats   => statsHolder.foreach(holder => sender ! holder.toStats)
    case Http.ClearStats => statsHolder.foreach(_.clear())

    case Http.Unbind =>
      tcpListener ! Tcp.Unbind
      context become unbinding(sender)
  }

  def unbinding(unbindCommander: ActorRef): Receive = {
    case Tcp.Unbound =>
      unbindCommander ! Http.Unbound
      context stop self
  }
}
