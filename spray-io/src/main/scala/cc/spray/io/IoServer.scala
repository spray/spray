/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.io

import java.net.{SocketAddress, InetSocketAddress}
import akka.actor.ActorRef

abstract class IoServer(val ioWorker: IoWorker) extends IoPeer {
  import IoServer._
  var bindingKey: Option[Key] = None
  var endpoint: Option[SocketAddress] = None
  var state = unbound

  def receive = {
    new Receive {
      def isDefinedAt(x: Any) = state.isDefinedAt(x)
      def apply(x: Any) { state(x) }
    } orElse {
      case x: CommandError => log.warning("Received {}", x)
    }
  }

  lazy val unbound: Receive = {
    case x: Bind =>
      log.debug("Starting {} on {}", self.path, x.endpoint)
      endpoint = Some(x.endpoint)
      state = binding
      ioWorker ! IoWorker.Bind(self, x.endpoint, x.bindingBacklog, sender)

    case x: ServerCommand => sender ! CommandError(x, "Not yet bound")
  }

  lazy val binding: Receive = {
    case IoWorker.Bound(key, receiver: ActorRef) =>
      bindingKey = Some(key)
      state = bound
      log.info("{} started on {}", self.path, endpoint.get)
      receiver ! Bound(endpoint.get)

    case x: ServerCommand => sender ! CommandError(x, "Still binding")
  }

  lazy val bound: Receive = {
    case x: IoWorker.Connected =>
      ioWorker ! IoWorker.Register(createConnectionHandle(x.key))

    case Unbind =>
      log.debug("Stopping {} on {}", self.path, endpoint.get)
      state = unbinding
      ioWorker ! IoWorker.Unbind(bindingKey.get, sender)

    case x: ServerCommand => sender ! CommandError(x, "Already bound")
  }

  lazy val unbinding: Receive = {
    case IoWorker.Unbound(_, receiver: ActorRef) =>
      log.info("{} stopped on {}", self.path, endpoint.get)
      state = unbound
      receiver ! Unbound(endpoint.get)
      bindingKey = None
      endpoint = None

    case x: ServerCommand => sender ! CommandError(x, "Still unbinding")
  }
}

object IoServer {

  ////////////// COMMANDS //////////////
  sealed trait ServerCommand extends Command
  case class Bind(endpoint: SocketAddress, bindingBacklog: Int) extends ServerCommand
  object Bind {
    def apply(interface: String, port: Int, bindingBacklog: Int = 100): Bind =
      Bind(new InetSocketAddress(interface, port), bindingBacklog)
  }

  case object Unbind extends ServerCommand
  type Close = IoPeer.Close;        val Close = IoPeer.Close
  type Send = IoPeer.Send;          val Send = IoPeer.Send
  type Dispatch = IoPeer.Dispatch;  val Dispatch = IoPeer.Dispatch // only available with ConnectionActors mixin


  ////////////// EVENTS //////////////
  case class Bound(endpoint: SocketAddress)
  case class Unbound(endpoint: SocketAddress)
  type Closed = IoPeer.Closed;                val Closed = IoPeer.Closed
  type SendCompleted = IoPeer.SendCompleted;  val SendCompleted = IoPeer.SendCompleted
  type Received = IoPeer.Received;            val Received = IoPeer.Received

}