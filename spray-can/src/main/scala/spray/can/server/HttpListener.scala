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

package spray.can.server

import scala.concurrent.duration._
import akka.io.{ IO, Tcp }
import akka.actor._
import spray.can.server.StatsSupport.StatsHolder
import spray.can.{ HttpExt, Http }
import spray.io.TickGenerator.Tick
import spray.util.Timestamp

private[can] class HttpListener(bindCommander: ActorRef,
                                bind: Http.Bind,
                                httpSettings: HttpExt#Settings) extends Actor with ActorLogging {
  import context.system
  import bind._

  private val connectionCounter = Iterator from 0
  private val settings = bind.settings getOrElse ServerSettings(system)
  private val statsHolder = if (settings.statsSupport) Some(new StatsHolder) else None
  private val pipelineStage = HttpServerConnection.pipelineStage(settings, statsHolder)

  context.watch(listener)

  log.debug("Binding to {}", endpoint)

  IO(Tcp) ! Tcp.Bind(self, endpoint, backlog, options)

  context.setReceiveTimeout(settings.bindTimeout)

  // we cannot sensibly recover from crashes
  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def receive = binding

  def binding: Receive = {
    case x: Tcp.Bound ⇒
      log.info("Bound to {}", endpoint)
      bindCommander ! x
      context.setReceiveTimeout(Duration.Undefined)
      context.become(connected(sender()))

    case Tcp.CommandFailed(_: Tcp.Bind) ⇒
      log.warning("Bind to {} failed", endpoint)
      bindCommander ! Http.CommandFailed(bind)
      context.stop(self)

    case ReceiveTimeout ⇒
      log.warning("Bind to {} failed, timeout {} expired", endpoint, settings.bindTimeout)
      bindCommander ! Http.CommandFailed(bind)
      context.stop(self)

    case Http.Unbind(_) ⇒ // no children possible, so no reason to note the timeout
      log.info("Bind to {} aborted", endpoint)
      bindCommander ! Http.CommandFailed(bind)
      context.become(bindingAborted(Set(sender())))
  }
  /** Waiting for the bind to execute to close it down instantly afterwards */
  def bindingAborted(unbindCommanders: Set[ActorRef]): Receive = {
    case _: Tcp.Bound ⇒ unbind(sender(), unbindCommanders, Duration.Zero)
    case Tcp.CommandFailed(_: Tcp.Bind) ⇒
      unbindCommanders foreach (_ ! Http.Unbound)
      context.stop(self)

    case ReceiveTimeout ⇒
      unbindCommanders foreach (_ ! Http.Unbound)
      context.stop(self)

    case Http.Unbind(_) ⇒ context.become(bindingAborted(unbindCommanders + sender()))
  }

  def connected(tcpListener: ActorRef): Receive = {
    case Tcp.Connected(remoteAddress, localAddress) ⇒
      val conn = sender()
      context.actorOf(
        props = Props(new HttpServerConnection(conn, listener, pipelineStage, remoteAddress, localAddress, settings))
          .withDispatcher(httpSettings.ConnectionDispatcher),
        name = connectionCounter.next().toString)

    case Http.GetStats            ⇒ statsHolder foreach { holder ⇒ sender() ! holder.toStats }
    case Http.ClearStats          ⇒ statsHolder foreach { _.clear() }

    case Http.Unbind(timeout)     ⇒ unbind(tcpListener, Set(sender()), timeout)

    case _: Http.ConnectionClosed ⇒
    // ignore, we receive this event when the user didn't register the handler within the registration timeout period
  }

  def unbind(tcpListener: ActorRef, unbindCommanders: Set[ActorRef], timeout: Duration): Unit = {
    tcpListener ! Tcp.Unbind
    context.setReceiveTimeout(settings.unbindTimeout)
    context.become(unbinding(unbindCommanders, timeout))
  }

  def unbinding(commanders: Set[ActorRef], gracePeriodTimeout: Duration): Receive = {
    case Tcp.Unbound ⇒ // normal path
      log.info("Unbound from {}", endpoint)
      commanders foreach (_ ! Http.Unbound)
      if (context.children.isEmpty) context.stop(self)
      else {
        context.setReceiveTimeout(Duration.Undefined)
        self ! Tick
        context.become(gracePeriod(Timestamp.now + gracePeriodTimeout))
      }

    case ReceiveTimeout ⇒
      log.warning("Unbinding from {} failed, timeout {} expired, stopping", endpoint, settings.unbindTimeout)
      commanders foreach (_ ! Http.CommandFailed(Http.Unbind))
      context.stop(self)

    case Http.Unbind(timeout) ⇒
      // a latter Unbind overrides a previous timeout
      context.become(unbinding(commanders + sender(), timeout))
  }
  /** Wait for a last grace period to expire before shutting us (and our children down) */
  def gracePeriod(timeout: Timestamp): Receive = {
    case Tick ⇒
      if (timeout.isPast || context.children.isEmpty) context.stop(self)
      else context.system.scheduler.scheduleOnce(1.second, self, Tick)(context.dispatcher)
  }
}
