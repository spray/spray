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

import akka.actor._
import java.nio.ByteBuffer
import java.net.InetSocketAddress
import spray.util.ConnectionClosedReason


abstract class IOPeer extends Actor with ActorLogging {

  def ioBridge: IOBridge

  protected def createConnectionHandle(key: Key, remoteAddress: InetSocketAddress, localAddress: InetSocketAddress,
                                       commander: ActorRef, tag: Any): Handle =
    SimpleHandle(key, self, remoteAddress, localAddress, commander, tag) // default implementation
}

object IOPeer {

  ////////////// COMMANDS //////////////
  case class Close(reason: ConnectionClosedReason) extends Command
  case class Send(buffers: Seq[ByteBuffer], ack: Option[Any]) extends Command
  object Send {
    def apply(buffer: ByteBuffer): Send = apply(buffer, None)
    def apply(buffer: ByteBuffer, ack: Option[Any]): Send = new Send(buffer :: Nil, ack)
  }

  case object StopReading extends Command
  case object ResumeReading extends Command

  // only available with ConnectionActors mixin
  case class Tell(receiver: ActorRef, message: Any, sender: ActorRef) extends Command

  ////////////// EVENTS //////////////
  type Closed = IOBridge.Closed;     val Closed = IOBridge.Closed
  type Received = IOBridge.Received; val Received = IOBridge.Received

  // only available with ConnectionActors mixin
  case class ActorDeath(actor: ActorRef) extends Event
  case class AckEvent(ack: Any) extends Event
}