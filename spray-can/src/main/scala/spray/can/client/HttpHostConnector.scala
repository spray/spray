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

package spray.can.client

import scala.collection.immutable.Queue
import akka.actor._
import spray.http.{ Uri, HttpHeaders, HttpRequest }
import spray.can.Http

private[can] class HttpHostConnector(normalizedSetup: Http.HostConnectorSetup, clientConnectionSettingsGroup: ActorRef)
    extends Actor with ActorLogging {

  import HttpHostConnector._
  import normalizedSetup.{ settings ⇒ _, _ }
  def settings = normalizedSetup.settings.get

  private[this] val counter = Iterator from 0
  private[this] val dispatchStrategy = if (settings.pipelining) new PipelinedStrategy else new NonPipelinedStrategy
  private[this] var openRequestCounts = Map.empty[ActorRef, Int] // open requests per child, holds -1 if unconnected
  private[this] val headers =
    if (defaultHeaders.exists(_.isInstanceOf[HttpHeaders.Host])) defaultHeaders
    else {
      import Uri._
      val Authority(_, normalizedPort, _) = Authority(Host(host), port).normalizedForHttp(sslEncryption)
      HttpHeaders.Host(host, normalizedPort) :: defaultHeaders
    }

  context.setReceiveTimeout(settings.idleTimeout)

  // we cannot sensibly recover from crashes
  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def receive: Receive = {
    case request: HttpRequest ⇒
      val requestWithDefaultHeaders = request.withDefaultHeaders(headers)
      dispatchStrategy(RequestContext(requestWithDefaultHeaders, settings.maxRetries, settings.maxRedirects, sender))

    case ctx: RequestContext ⇒
      // either a retry or redirect
      // ensure the default headers are set in case this is a redirect from another host
      val requestWithDefaultHeaders = ctx.request.withDefaultHeaders(headers)
      dispatchStrategy(ctx.copy(request = requestWithDefaultHeaders))

    case RequestCompleted ⇒
      openRequestCounts = openRequestCounts.updated(sender, openRequestCounts(sender) - 1)
      dispatchStrategy.onConnectionStateChange()

    case Http.CloseAll(cmd) ⇒
      val stillConnected = openRequestCounts.foldLeft(Set.empty[ActorRef]) {
        case (acc, (_, -1))  ⇒ acc
        case (acc, (ref, _)) ⇒ ref ! cmd; acc + ref
      }
      if (stillConnected.isEmpty) {
        sender ! Http.ClosedAll
        context.stop(self)
      } else context.become(closing(stillConnected, Set(sender)))

    case Disconnected(rescheduledRequestCount) ⇒
      val oldCount = openRequestCounts(sender)
      val newCount =
        if (oldCount == rescheduledRequestCount) -1 // "normal" case when a connection was closed
        else oldCount - rescheduledRequestCount // we have already scheduled a new request onto this connection
      openRequestCounts = openRequestCounts.updated(sender, newCount)
      dispatchStrategy.onConnectionStateChange()

    case Terminated(child) ⇒
      openRequestCounts -= child
      dispatchStrategy.onConnectionStateChange()

    case DemandIdleShutdown ⇒
      openRequestCounts -= sender
      sender ! PoisonPill

    case ReceiveTimeout ⇒
      if (context.children.isEmpty) {
        log.debug("Initiating idle shutdown")
        context.parent ! DemandIdleShutdown
        context.become { // after having initiated our shutdown we must be bounce all requests
          case request: HttpRequest ⇒ context.parent.forward(request -> normalizedSetup)
          case _: Http.CloseAll     ⇒ sender ! Http.ClosedAll; context.stop(self)
        }
      }
  }

  def closing(connected: Set[ActorRef], commanders: Set[ActorRef]): Receive = {
    case Http.CloseAll(cmd) ⇒
      context.become(closing(connected, commanders + sender))

    case Terminated(child) ⇒
      val stillConnected = connected - child
      if (stillConnected.isEmpty) {
        commanders foreach (_ ! Http.ClosedAll)
        context.stop(self)
      } else context.become(closing(stillConnected, commanders))

    case ReceiveTimeout ⇒ context.stop(self)

    case _: Disconnected | RequestCompleted | DemandIdleShutdown ⇒ // ignore
  }

  def firstIdleConnection: Option[ActorRef] = openRequestCounts.find(_._2 == 0).map(_._1)

  def firstUnconnectedConnection: Option[ActorRef] = openRequestCounts.find(_._2 == -1).map(_._1) orElse {
    if (context.children.size < settings.maxConnections) Some(newConnectionChild()) else None
  }

  def newConnectionChild(): ActorRef = {
    val child = context.watch {
      context.actorOf(
        props = Props(new HttpHostConnectionSlot(host, port, sslEncryption, options, settings.idleTimeout,
          clientConnectionSettingsGroup)),
        name = counter.next().toString)
    }
    openRequestCounts = openRequestCounts.updated(child, -1)
    child
  }

  def dispatch(ctx: RequestContext, connection: ActorRef): Unit = {
    connection ! ctx
    val currentCount = openRequestCounts(connection)
    openRequestCounts = openRequestCounts.updated(connection, Math.max(currentCount + 1, 1)) // -1 is increased to 1
  }

  sealed trait DispatchStrategy {
    def apply(ctx: RequestContext)
    def onConnectionStateChange()
  }

  /**
   * Defines a DispatchStrategy with the following logic:
   *  - Dispatch to the first idle connection in the pool, if there is one.
   *  - If none is idle dispatch to the first unconnected connection, if there is one.
   *  - If all are already connected, store the request and send it as soon as one
   *    connection becomes either idle or unconnected.
   */
  class NonPipelinedStrategy extends DispatchStrategy {
    var queue = Queue.empty[RequestContext]

    def apply(ctx: RequestContext): Unit =
      findAvailableConnection match {
        case Some(connection) ⇒ dispatch(ctx, connection)
        case None             ⇒ queue = queue.enqueue(ctx)
      }

    def onConnectionStateChange(): Unit =
      if (queue.nonEmpty)
        findAvailableConnection foreach { connection ⇒
          dispatch(queue.head, connection)
          queue = queue.tail
        }

    def findAvailableConnection = firstIdleConnection orElse firstUnconnectedConnection
  }

  /**
   * Defines a DispatchStrategy with the following logic:
   *  - Dispatch to the first idle connection in the pool, if there is one.
   *  - If none is idle, dispatch to the first unconnected connection, if there is one.
   *  - If all are already connected, dispatch to the connection with the least open requests.
   */
  class PipelinedStrategy extends DispatchStrategy {
    def apply(ctx: RequestContext): Unit = {
      // if possible dispatch to idle connections, if no idle ones are available prefer
      // unconnected connections over busy ones and less busy ones over more busy ones
      val connection = firstIdleConnection orElse firstUnconnectedConnection getOrElse {
        openRequestCounts.minBy(_._2)._1
      }
      dispatch(ctx, connection)
    }

    def onConnectionStateChange(): Unit = {}
  }
}

private[can] object HttpHostConnector {
  case class RequestContext(request: HttpRequest, retriesLeft: Int, redirectsLeft: Int, commander: ActorRef)
  case class Disconnected(rescheduledRequestCount: Int)
  case object RequestCompleted
  case object DemandIdleShutdown
}
