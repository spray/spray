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
import akka.event.Logging
import akka.spray.RefUtils
import spray.util.Reply


abstract class IOServer(val rootIoBridge: ActorRef) extends IOPeer {
  import IOServer._
  require(RefUtils.isLocal(rootIoBridge), "An IOServer must live in the same JVM as the IOBridge it is to use")
  private[this] val debug = TaggableLog(log, Logging.DebugLevel)
  private[this] val info = TaggableLog(log, Logging.InfoLevel)
  private[this] var state = unbound

  def receive: Receive = {
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

  def unbound: Receive = {
    case Bind(endpoint, bindingBacklog, tag) =>
      debug.log(tag, "Starting {} on {}", self.path, endpoint)
      state = binding(endpoint)
      rootIoBridge.tell(
        msg = IOBridge.Bind(endpoint, bindingBacklog, tag),
        sender = Reply.withContext(sender)
      )

    case x: ServerCommand =>
      sender ! Status.Failure(CommandException(x, "Not yet bound"))
  }

  def binding(endpoint: InetSocketAddress): Receive = {
    case Reply(IOBridge.Bound(key, tag), commander: ActorRef) =>
      state = bound(endpoint, key, tag)
      info.log(tag, "{} started on {}", self.path, endpoint)
      commander ! Bound(endpoint, tag)

    case x: ServerCommand =>
      sender ! Status.Failure(CommandException(x, "Still binding"))
  }

  def bound(endpoint: InetSocketAddress, bindingKey: IOBridge.Key, bindingTag: Any): Receive = {
    case Reply(IOBridge.Connected(key, tag), commander: ActorRef) =>
      val handle = createConnectionHandle(key, sender, commander, tag)
      sender ! IOBridge.Register(handle)

    case Unbind =>
      debug.log(bindingTag, "Stopping {} on {}", self.path, endpoint)
      state = unbinding(endpoint)
      rootIoBridge.tell(IOBridge.Unbind(bindingKey), Reply.withContext(sender))

    case x: ServerCommand =>
      sender ! Status.Failure(CommandException(x, "Already bound"))
  }

  def unbinding(endpoint: InetSocketAddress): Receive = {
    case Reply(IOBridge.Unbound(_, tag), originalSender: ActorRef) =>
      info.log(tag, "{} stopped on {}", self.path, endpoint)
      state = unbound
      originalSender ! Unbound(endpoint, tag)

    case x: ServerCommand =>
      sender ! Status.Failure(CommandException(x, "Still unbinding"))
  }
}

object IOServer {

  ////////////// COMMANDS //////////////
  sealed trait ServerCommand extends Command
  case class Bind(endpoint: InetSocketAddress, bindingBacklog: Int, tag: Any) extends ServerCommand
  object Bind {
    def apply(interface: String, port: Int, bindingBacklog: Int = 100, tag: Any = ()): Bind =
      Bind(new InetSocketAddress(interface, port), bindingBacklog, tag)
  }

  case object Unbind extends ServerCommand
  type Close = IOPeer.Close;  val Close = IOPeer.Close
  type Send = IOPeer.Send;    val Send = IOPeer.Send
  val StopReading = IOPeer.StopReading
  val ResumeReading = IOPeer.ResumeReading
  type Tell = IOPeer.Tell;    val Tell = IOPeer.Tell // only available with ConnectionActors mixin


  ////////////// EVENTS //////////////
  case class Bound(endpoint: InetSocketAddress, tag: Any)
  case class Unbound(endpoint: InetSocketAddress, tag: Any)
  type Closed = IOPeer.Closed;         val Closed = IOPeer.Closed
  type AckEvent = IOPeer.AckEvent;     val AckEvent = IOPeer.AckEvent
  type Received = IOPeer.Received;     val Received = IOPeer.Received
  type ActorDeath = IOPeer.ActorDeath; val ActorDeath = IOPeer.ActorDeath
}