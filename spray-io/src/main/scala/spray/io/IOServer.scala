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

package spray.io

import java.net.InetSocketAddress
import akka.actor.{Status, ActorRef}
import spray.util.Reply

abstract class IOServer(val ioBridge: IOBridge) extends IOPeer {
  import IOServer._
  private var bindingKey: Option[Key] = None
  private var endpoint: Option[InetSocketAddress] = None
  private var state = unbound

  def receive = {
    new Receive {
      def isDefinedAt(x: Any) = state.isDefinedAt(x)
      def apply(x: Any) { state(x) }
    } orElse {
      case _: Closed =>
        // by default we drop information about a closed connection here

      case Status.Failure(error) =>
        log.warning("Received {}", error)
    }
  }

  lazy val unbound: Receive = {
    case Bind(endpoint, bindingBacklog) =>
      log.debug("Starting {} on {}", self.path, endpoint)
      this.endpoint = Some(endpoint)
      state = binding
      val replyWithCommander = Reply.withContext(sender)
      ioBridge.tell(IOBridge.Bind(replyWithCommander, endpoint, bindingBacklog), replyWithCommander)

    case x: ServerCommand =>
      sender ! Status.Failure(CommandException(x, "Not yet bound"))
  }

  lazy val binding: Receive = {
    case Reply(IOBridge.Bound(key, tag), commander: ActorRef) =>
      bindingKey = Some(key)
      state = bound
      log.info("{} started on {}", self.path, endpoint.get)
      commander ! Bound(endpoint.get, tag)

    case x: ServerCommand =>
      sender ! Status.Failure(CommandException(x, "Still binding"))
  }

  lazy val bound: Receive = {
    case Reply(IOBridge.Connected(key, remoteAddress, localAddress, tag), commander: ActorRef) =>
      val handle = createConnectionHandle(key, remoteAddress, localAddress, commander, tag)
      ioBridge ! IOBridge.Register(handle)

    case Unbind =>
      log.debug("Stopping {} on {}", self.path, endpoint.get)
      state = unbinding
      ioBridge.tell(IOBridge.Unbind(bindingKey.get), Reply.withContext(sender))

    case x: ServerCommand =>
      sender ! Status.Failure(CommandException(x, "Already bound"))
  }

  lazy val unbinding: Receive = {
    case Reply(_: IOBridge.Unbound, originalSender: ActorRef) =>
      log.info("{} stopped on {}", self.path, endpoint.get)
      state = unbound
      originalSender ! Unbound(endpoint.get)
      bindingKey = None
      endpoint = None

    case x: ServerCommand =>
      sender ! Status.Failure(CommandException(x, "Still unbinding"))
  }
}

object IOServer {

  ////////////// COMMANDS //////////////
  sealed trait ServerCommand extends Command
  case class Bind(endpoint: InetSocketAddress, bindingBacklog: Int) extends ServerCommand
  object Bind {
    def apply(interface: String, port: Int, bindingBacklog: Int = 100): Bind =
      Bind(new InetSocketAddress(interface, port), bindingBacklog)
  }

  case object Unbind extends ServerCommand
  type Close = IOPeer.Close;  val Close = IOPeer.Close
  type Send = IOPeer.Send;    val Send = IOPeer.Send
  val StopReading = IOPeer.StopReading
  val ResumeReading = IOPeer.ResumeReading
  type Tell = IOPeer.Tell;    val Tell = IOPeer.Tell // only available with ConnectionActors mixin


  ////////////// EVENTS //////////////
  case class Bound(endpoint: InetSocketAddress, tag: Any)
  case class Unbound(endpoint: InetSocketAddress)
  type Closed = IOPeer.Closed;         val Closed = IOPeer.Closed
  type AckEvent = IOPeer.AckEvent;     val AckEvent = IOPeer.AckEvent
  type Received = IOPeer.Received;     val Received = IOPeer.Received
  type ActorDeath = IOPeer.ActorDeath; val ActorDeath = IOPeer.ActorDeath
}