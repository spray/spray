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

package spray.can.server

import akka.util.Duration
import akka.io.{ ExtraStrategies, IO, Tcp }
import akka.actor._
import spray.util.SprayActorLogging
import spray.can.server.StatsSupport.StatsHolder
import spray.can.{ HttpExt, Http }

private[can] class HttpListener(bindCommander: ActorRef,
                                bind: Http.Bind,
                                httpSettings: HttpExt#Settings) extends Actor with SprayActorLogging {
  import context.system
  import bind._

  val connectionCounter = Iterator from 0
  val settings = bind.settings getOrElse ServerSettings(system)
  val statsHolder = if (settings.statsSupport) Some(new StatsHolder) else None
  val pipelineStage = HttpServerConnection.pipelineStage(settings, statsHolder)

  context.watch(listener)

  log.debug("Binding to {}", endpoint)

  IO(Tcp) ! Tcp.Bind(self, endpoint, backlog, options)

  context.setReceiveTimeout(settings.bindTimeout)

  // we cannot sensibly recover from crashes
  override def supervisorStrategy = ExtraStrategies.stoppingStrategy

  def receive = binding()

  def binding(unbindCommanders: Set[ActorRef] = Set.empty): Receive = {
    case _: Tcp.Bound if !unbindCommanders.isEmpty ⇒
      log.info("Bind to {} aborted", endpoint)
      bindCommander ! Http.CommandFailed(bind)
      context.setReceiveTimeout(settings.unbindTimeout)
      context.become(unbinding(unbindCommanders))

    case x: Tcp.Bound ⇒
      log.info("Bound to {}", endpoint)
      bindCommander ! x
      context.resetReceiveTimeout()
      context.become(connected(sender))

    case Tcp.CommandFailed(_: Tcp.Bind) ⇒
      log.warning("Bind to {} failed", endpoint)
      bindCommander ! Http.CommandFailed(bind)
      unbindCommanders foreach (_ ! Http.Unbound)
      context.stop(self)

    case ReceiveTimeout ⇒
      log.warning("Bind to {} failed, timeout {} expired", endpoint, settings.bindTimeout)
      bindCommander ! Http.CommandFailed(bind)
      unbindCommanders foreach (_ ! Http.Unbound)
      context.stop(self)

    case Http.Unbind ⇒
      log.debug("Aborting bind to {}", endpoint)
      context.become(binding(unbindCommanders + sender))
  }

  def connected(tcpListener: ActorRef): Receive = {
    case Tcp.Connected(remoteAddress, localAddress) ⇒
      val conn = sender
      context.actorOf(
        props = Props(new HttpServerConnection(conn, listener, pipelineStage, remoteAddress, localAddress, settings))
          .withDispatcher(httpSettings.ConnectionDispatcher),
        name = connectionCounter.next().toString)

    case Http.GetStats   ⇒ statsHolder foreach { holder ⇒ sender ! holder.toStats }
    case Http.ClearStats ⇒ statsHolder foreach { _.clear() }

    case Http.Unbind ⇒
      tcpListener ! Tcp.Unbind
      context.setReceiveTimeout(settings.unbindTimeout)
      context.become(unbinding(Set(sender)))

    case _: Http.ConnectionClosed ⇒
    // ignore, we receive this event when the user didn't register the handler within the registration timeout period
  }

  def unbinding(commanders: Set[ActorRef]): Receive = {
    case Http.Unbind ⇒
      context.become(unbinding(commanders + sender))

    case Tcp.Unbound ⇒
      log.info("Unbound from {}", endpoint)
      commanders foreach (_ ! Http.Unbound)
      context.stop(self)

    case ReceiveTimeout ⇒
      log.warning("Unbinding from {} failed, timeout {} expired, stopping", endpoint, settings.unbindTimeout)
      commanders foreach (_ ! Http.CommandFailed(Http.Unbind))
      context.stop(self)
  }
}
