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

import java.net.{InetSocketAddress, SocketAddress}
import cc.spray.util.Reply
import akka.actor.{Status, ActorRef}

class IoClient(val ioWorker: IoWorker) extends IoPeer {
  import IoClient._

  override def preStart() {
    log.info("Starting {}", self.path)
  }

  override def postStop() {
    log.info("Stopped {}", self.path)
  }

  protected def receive = {
    case Connect(address) =>
      ioWorker.tell(IoWorker.Connect(address), Reply.withContext(sender))

    case Reply(IoWorker.Connected(key), originalSender: ActorRef) =>
      val handle = createConnectionHandle(key)
      ioWorker ! IoWorker.Register(handle)
      originalSender ! Connected(handle)

    case Status.Failure(error) =>
      log.warning("Received {}", error)
  }

}

object IoClient {

  ////////////// COMMANDS //////////////
  case class Connect(address: SocketAddress)
  object Connect {
    def apply(host: String, port: Int): Connect = Connect(new InetSocketAddress(host, port))
  }
  type Close = IoPeer.Close;  val Close = IoPeer.Close
  type Send = IoPeer.Send;    val Send = IoPeer.Send
  val StopReading = IoPeer.StopReading
  val ResumeReading = IoPeer.ResumeReading
  type Tell = IoPeer.Tell;    val Tell = IoPeer.Tell // only available with ConnectionActors mixin

  ////////////// EVENTS //////////////
  case class Connected(handle: Handle)
  type Closed = IoPeer.Closed;                val Closed = IoPeer.Closed
  type SendCompleted = IoPeer.SendCompleted;  val SendCompleted = IoPeer.SendCompleted
  type Received = IoPeer.Received;            val Received = IoPeer.Received

}