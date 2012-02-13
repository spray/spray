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

import java.nio.ByteBuffer
import java.net.SocketAddress
import akka.actor.{ActorRef}

sealed trait Command {
  def errorReceiver: Option[ActorRef]
}

// "super" commands not on the connection-level
case class Stop(ackTo: Option[ActorRef] = None) extends Command {
  def errorReceiver = ackTo
}

case class Bind(
  handleCreator: ActorRef,
  address: SocketAddress,
  backlog: Int = 100,
  ackTo: Option[ActorRef] = None
) extends Command {
  def errorReceiver = ackTo
}

case class Unbind(bindingKey: Key, ackTo: Option[ActorRef] = None) extends Command {
  def errorReceiver = ackTo
}

case class Connect(handleCreator: ActorRef, address: SocketAddress, tag: Any) extends Command {
  def errorReceiver = Some(handleCreator)
}

case class GetStats(deliverTo: ActorRef) extends Command {
  def errorReceiver = Some(deliverTo)
}

// commands on the connection-level
sealed abstract class ConnectionCommand extends Command {
  def handle: Handle
  def errorReceiver = Some(handle.handler)
}

case class Register(handle: Handle) extends ConnectionCommand

case class Close(handle: Handle, reason: ConnectionClosedReason) extends ConnectionCommand

case class Send(handle: Handle, buffers: Seq[ByteBuffer]) extends ConnectionCommand