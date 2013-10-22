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
import akka.io.ExtraStrategies
import akka.actor._
import spray.http.{ Uri, HttpHeaders, HttpRequest }
import spray.can.Http

private[can] class HttpHostConnector(normalizedSetup: Http.HostConnectorSetup, clientConnectionSettingsGroup: ActorRef)
    extends Actor with ActorLogging {

  import HttpHostConnector._
  import normalizedSetup.{ settings ⇒ _, _ }
  def settings = normalizedSetup.settings.get

  private[this] val counter = Iterator from 0
  private[this] val dispatchStrategy = if (settings.pipelining) pipelinedStrategy else nonPipelinedStrategy
  private[this] var slotStates = Map.empty[ActorRef, SlotState] // state per child
  private[this] val headers =
    if (defaultHeaders.exists(_.isInstanceOf[HttpHeaders.Host])) defaultHeaders
    else {
      import Uri._
      val Authority(_, normalizedPort, _) = Authority(Host(host), port).normalizedForHttp(sslEncryption)
      HttpHeaders.Host(host, normalizedPort) :: defaultHeaders
    }

  if (settings.idleTimeout.isFinite) context.setReceiveTimeout(settings.idleTimeout)

  // we cannot sensibly recover from crashes
  override def supervisorStrategy() = ExtraStrategies.stoppingStrategy

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
      slotStates = slotStates.updated(sender, slotStates(sender).dequeueOne)
      dispatchStrategy.onConnectionStateChange()

    case Http.CloseAll(cmd) ⇒
      val stillConnected = slotStates.foldLeft(Set.empty[ActorRef]) {
        case (acc, (_, SlotState.Unconnected)) ⇒ acc
        case (acc, (ref, _))                   ⇒ ref ! cmd; acc + ref
      }
      if (stillConnected.isEmpty) {
        sender ! Http.ClosedAll
        context.stop(self)
      } else context.become(closing(stillConnected, Set(sender)))

    case Disconnected(rescheduledRequestCount) ⇒
      val newState =
        slotStates(sender) match {
          case SlotState.Connected(reqs) if reqs.size == rescheduledRequestCount ⇒
            SlotState.Unconnected // "normal" case when a connection was closed
          case SlotState.Connected(reqs) if reqs.size > rescheduledRequestCount ⇒
            SlotState.Connected(reqs drop rescheduledRequestCount) // we've already scheduled new requests onto this connection
          case x ⇒ throw new IllegalStateException("Unexpected slot state: " + x)
        }
      slotStates = slotStates.updated(sender, newState)
      dispatchStrategy.onConnectionStateChange()

    case Terminated(child) ⇒
      slotStates -= child
      dispatchStrategy.onConnectionStateChange()

    case DemandIdleShutdown ⇒
      slotStates -= sender
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

  def findChild(predicate: PartialFunction[SlotState, Boolean]): Option[ActorRef] =
    slotStates find { case (child, state) ⇒ predicate.isDefinedAt(state) && predicate(state) } map { _._1 }

  def firstIdleConnection: Option[ActorRef] = findChild { case SlotState.Connected(reqs) ⇒ reqs.isEmpty }

  def firstUnconnectedConnection: Option[ActorRef] = findChild { case SlotState.Unconnected ⇒ true } orElse {
    if (context.children.size < settings.maxConnections) Some(newConnectionChild()) else None
  }

  def newConnectionChild(): ActorRef = {
    val child = context.watch {
      context.actorOf(
        props = Props(new HttpHostConnectionSlot(host, port, sslEncryption, options, settings.idleTimeout,
          clientConnectionSettingsGroup)),
        name = counter.next().toString)
    }
    slotStates = slotStates.updated(child, SlotState.Unconnected)
    child
  }

  def dispatch(ctx: RequestContext, connection: ActorRef): Unit = {
    connection ! ctx
    val currentState = slotStates(connection)
    slotStates = slotStates.updated(connection, currentState.enqueue(ctx.request))
  }

  sealed abstract class DispatchStrategy {
    private[this] var queue = Queue.empty[RequestContext]

    def apply(ctx: RequestContext): Unit =
      pickConnection match {
        case Some(connection) ⇒ dispatch(ctx, connection)
        case None             ⇒ queue = queue.enqueue(ctx)
      }

    def onConnectionStateChange(): Unit =
      if (queue.nonEmpty)
        pickConnection foreach { connection ⇒
          dispatch(queue.head, connection)
          queue = queue.tail
        }

    // picks a connection to schedule the next request to, returns None if no connection
    // is currently available and the next request therefore needs to be queued
    protected def pickConnection: Option[ActorRef]
  }

  /**
   * Defines a DispatchStrategy with the following logic:
   *  - Dispatch to the first idle connection in the pool, if there is one.
   *  - If none is idle dispatch to the first unconnected connection, if there is one.
   *  - If all are already connected store the request and send it as soon as one
   *    connection becomes either idle or unconnected.
   */
  def nonPipelinedStrategy: DispatchStrategy =
    new DispatchStrategy {
      def pickConnection = firstIdleConnection orElse firstUnconnectedConnection
    }

  /**
   * Defines a DispatchStrategy with the following logic:
   *  - Dispatch to the first idle connection in the pool, if there is one.
   *  - If none is idle dispatch to the first unconnected connection, if there is one.
   *  - If all are already connected dispatch to the connection with the least open requests
   *    that only has requests with idempotent methods scheduled to it
   *  - If all connections currently have non-idempotent requests open store the request
   *    and send it as soon as a connection becomes available.
   */
  def pipelinedStrategy: DispatchStrategy =
    new DispatchStrategy {
      def pickConnection = firstIdleConnection orElse firstUnconnectedConnection orElse {
        def onlyIdempotent: ((ActorRef, SlotState)) ⇒ Boolean = {
          case (child, SlotState.Connected(reqs)) ⇒ reqs.forall(_.method.isIdempotent)
          case (child, SlotState.Unconnected)     ⇒ true
        }
        slotStates.toSeq.filter(onlyIdempotent).sortBy(_._2.openRequestCount).headOption.map(_._1)
      }
    }
}

private[can] object HttpHostConnector {
  case class RequestContext(request: HttpRequest, retriesLeft: Int, redirectsLeft: Int, commander: ActorRef)
  case class Disconnected(rescheduledRequestCount: Int)
  case object RequestCompleted
  case object DemandIdleShutdown

  sealed trait SlotState {
    def enqueue(request: HttpRequest): SlotState
    def dequeueOne: SlotState
    def openRequestCount: Int
  }
  object SlotState {
    case class Connected(openRequests: Queue[HttpRequest]) extends SlotState {
      def enqueue(request: HttpRequest) = Connected(openRequests.enqueue(request))
      def dequeueOne = Connected(openRequests.tail)
      def openRequestCount = openRequests.size
    }
    case object Unconnected extends SlotState {
      def enqueue(request: HttpRequest) = Connected(Queue(request))
      def dequeueOne = throw new IllegalStateException
      def openRequestCount = 0
    }
  }
}
