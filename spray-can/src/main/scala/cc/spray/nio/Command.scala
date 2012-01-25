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

package cc.spray.nio

import java.nio.ByteBuffer
import akka.actor.ActorRef
import java.net.SocketAddress

sealed trait Command

sealed abstract class SuperCommand extends Command {
  def sender: ActorRef
}

case class Stop(sender: ActorRef) extends SuperCommand
case class Bind(sender: ActorRef, handleFactory: Key => Handle, address: SocketAddress, backlog: Int = 50) extends SuperCommand
case class Unbind(sender: ActorRef, bindingKey: Key) extends SuperCommand
case class Connect(sender: ActorRef, address: SocketAddress, handleFactory: Key => Handle) extends SuperCommand


sealed trait SuperCommandAck
case object Stopped extends SuperCommandAck
case class Bound(bindingKey: Key) extends SuperCommandAck
case class Unbound(bindingKey: Key) extends SuperCommandAck
case class CommandError(command: Command, error: Throwable) extends SuperCommandAck


sealed abstract class ConnectionMessage {
  def handle: Handle
}

sealed abstract class ConnectionCommand extends ConnectionMessage with Command
case class Close(handle: Handle) extends ConnectionCommand
case class Send(handle: Handle, buffers: Seq[ByteBuffer]) extends ConnectionCommand

sealed abstract class ConnectionEvent extends ConnectionMessage
case class Connected(handle: Handle) extends ConnectionEvent
case class Closed(handle: Handle, reason: Option[Throwable]) extends ConnectionEvent
case class CompletedSend(handle: Handle) extends ConnectionEvent
case class Received(handle: Handle, buffer: ByteBuffer) extends ConnectionEvent